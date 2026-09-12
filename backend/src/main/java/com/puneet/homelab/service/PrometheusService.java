package com.puneet.homelab.service;

import com.puneet.homelab.config.PrometheusProperties;
import com.puneet.homelab.model.ClusterHistory;
import com.puneet.homelab.model.ClusterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Queries the Prometheus HTTP API and assembles a {@link ClusterMetrics} snapshot.
 * Every query is best-effort: a failure yields a null field rather than failing the
 * whole response, so the frontend can degrade gracefully per-metric.
 */
@Service
public class PrometheusService {

    private static final Logger log = LoggerFactory.getLogger(PrometheusService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient client;
    private final PrometheusProperties props;

    public PrometheusService(WebClient.Builder builder, PrometheusProperties props) {
        this.props = props;
        this.client = builder.baseUrl(props.getUrl()).build();
    }

    public ClusterMetrics collect() {
        boolean reachable = true;
        PrometheusProperties.Queries q = props.getQueries();

        Double cpu = queryScalar(q.getCpuUsagePercent());
        Double cpuTemp = queryScalar(q.getCpuTempCelsius());
        Long memUsed = toLong(queryScalar(q.getMemoryUsedBytes()));
        Long memTotal = toLong(queryScalar(q.getMemoryTotalBytes()));
        Double rx = queryScalar(q.getNetworkRxBytesPerSec());
        Double tx = queryScalar(q.getNetworkTxBytesPerSec());

        // If every core query is null, Prometheus is likely unreachable.
        if (cpu == null && memTotal == null && rx == null) {
            reachable = false;
        }

        ClusterMetrics.Memory memory = null;
        if (memTotal != null && memTotal > 0) {
            double usedPct = memUsed != null ? (100.0 * memUsed / memTotal) : 0.0;
            memory = new ClusterMetrics.Memory(memUsed, memTotal, round(usedPct));
        }

        ClusterMetrics.Network network = new ClusterMetrics.Network(round(rx), round(tx));

        List<ClusterMetrics.Disk> disks = new ArrayList<>();
        for (PrometheusProperties.DiskMapping d : props.getDisks()) {
            Long size = toLong(queryScalar(
                    "node_filesystem_size_bytes{mountpoint=\"" + d.getMountpoint() + "\"}"));
            Long avail = toLong(queryScalar(
                    "node_filesystem_avail_bytes{mountpoint=\"" + d.getMountpoint() + "\"}"));
            Long used = (size != null && avail != null) ? (size - avail) : null;
            Double usedPct = (size != null && size > 0 && used != null)
                    ? round(100.0 * used / size) : null;
            Double temp = null;
            if (d.getTempChip() != null && !d.getTempChip().isBlank()) {
                temp = queryScalar(
                        "node_hwmon_temp_celsius{chip=\"" + d.getTempChip() + "\",sensor=\"temp1\"}");
            }
            disks.add(new ClusterMetrics.Disk(
                    d.getLabel(), d.getMountpoint(), used, size, usedPct, round(temp)));
        }

        // Cluster inventory counts (best-effort).
        Integer nodes = toInt(queryScalar("count(kube_node_info)"));
        Integer podsTotal = toInt(queryScalar("count(kube_pod_info)"));
        Integer podsRunning = toInt(queryScalar("sum(kube_pod_status_phase{phase=\"Running\"})"));
        Integer cpuCores = toInt(queryScalar("count(count by (cpu)(node_cpu_seconds_total))"));
        Long clusterMem = toLong(queryScalar("sum(node_memory_MemTotal_bytes)"));
        ClusterMetrics.Cluster cluster = new ClusterMetrics.Cluster(
                nodes, podsTotal, podsRunning, cpuCores, clusterMem);

        return new ClusterMetrics(round(cpu), round(cpuTemp), memory, network, disks, cluster, reachable);
    }

    /**
     * Builds a {@link ClusterHistory} over the last {@code windowSec} seconds using
     * Prometheus range queries. Each series is best-effort and may be empty.
     */
    public ClusterHistory range(int windowSec, int stepSec) {
        long end = Instant.now().getEpochSecond();
        long start = end - windowSec;
        PrometheusProperties.Queries q = props.getQueries();

        List<ClusterHistory.Point> cpu = queryRange(q.getCpuUsagePercent(), start, end, stepSec);
        // memoryUsedBytes is already (total - available); express used% directly.
        String memUsedPct = "100 * (" + q.getMemoryUsedBytes() + ") / " + q.getMemoryTotalBytes();
        List<ClusterHistory.Point> mem = queryRange(memUsedPct, start, end, stepSec);
        List<ClusterHistory.Point> rx = queryRange(q.getNetworkRxBytesPerSec(), start, end, stepSec);
        List<ClusterHistory.Point> tx = queryRange(q.getNetworkTxBytesPerSec(), start, end, stepSec);

        boolean reachable = !(cpu.isEmpty() && mem.isEmpty() && rx.isEmpty());
        return new ClusterHistory((long) start, (long) end, (long) stepSec,
                cpu, mem, rx, tx, reachable);
    }

    /**
     * Runs a PromQL range query and returns the matrix's first series as points.
     */
    @SuppressWarnings("unchecked")
    private List<ClusterHistory.Point> queryRange(String promql, long start, long end, int step) {
        List<ClusterHistory.Point> points = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
            URI uri = URI.create(props.getUrl() + "/api/v1/query_range?query=" + encoded
                    + "&start=" + start + "&end=" + end + "&step=" + step);

            Map<String, Object> body = client.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.debug("Prometheus range query failed [{}]: {}", promql, e.toString());
                        return Mono.empty();
                    })
                    .block();

            if (body == null) return points;
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return points;
            List<Map<String, Object>> result = (List<Map<String, Object>>) data.get("result");
            if (result == null || result.isEmpty()) return points;
            List<List<Object>> values = (List<List<Object>>) result.get(0).get("values");
            if (values == null) return points;
            for (List<Object> pair : values) {
                if (pair.size() < 2) continue;
                long t = (long) Double.parseDouble(pair.get(0).toString());
                double v = Double.parseDouble(pair.get(1).toString());
                points.add(new ClusterHistory.Point(t, Math.round(v * 100.0) / 100.0));
            }
        } catch (Exception e) {
            log.debug("Prometheus range error [{}]: {}", promql, e.toString());
        }
        return points;
    }

    /**
     * Runs an instant PromQL query and returns the first sample's value, or null.
     */
    @SuppressWarnings("unchecked")
    private Double queryScalar(String promql) {
        try {
            // Encode the PromQL ourselves and pass a ready-made URI so WebClient does
            // not re-encode the special characters ({ } = spaces [ ] etc.).
            String encoded = URLEncoder.encode(promql, StandardCharsets.UTF_8);
            URI uri = URI.create(props.getUrl() + "/api/v1/query?query=" + encoded);

            Map<String, Object> body = client.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.debug("Prometheus query failed [{}]: {}", promql, e.toString());
                        return Mono.empty();
                    })
                    .block();

            if (body == null) return null;
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data == null) return null;
            List<Map<String, Object>> result = (List<Map<String, Object>>) data.get("result");
            if (result == null || result.isEmpty()) return null;
            List<Object> value = (List<Object>) result.get(0).get("value");
            if (value == null || value.size() < 2) return null;
            return Double.parseDouble(value.get(1).toString());
        } catch (Exception e) {
            log.debug("Prometheus query error [{}]: {}", promql, e.toString());
            return null;
        }
    }

    private static Long toLong(Double d) {
        return d == null ? null : (long) (double) d;
    }

    private static Integer toInt(Double d) {
        return d == null ? null : (int) Math.round(d);
    }

    private static Double round(Double d) {
        return d == null ? null : Math.round(d * 100.0) / 100.0;
    }
}
