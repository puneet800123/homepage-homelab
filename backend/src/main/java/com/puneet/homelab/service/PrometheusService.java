package com.puneet.homelab.service;

import com.puneet.homelab.config.PrometheusProperties;
import com.puneet.homelab.model.ClusterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
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

        return new ClusterMetrics(round(cpu), round(cpuTemp), memory, network, disks, reachable);
    }

    /**
     * Runs an instant PromQL query and returns the first sample's value, or null.
     */
    @SuppressWarnings("unchecked")
    private Double queryScalar(String promql) {
        try {
            Map<String, Object> body = client.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/v1/query")
                            .queryParam("query", promql)
                            .build())
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

    private static Double round(Double d) {
        return d == null ? null : Math.round(d * 100.0) / 100.0;
    }
}
