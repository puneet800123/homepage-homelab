package com.puneet.homelab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configures the Prometheus endpoint plus the PromQL used to derive each cluster
 * metric. Queries are overridable via configuration so the dashboard can adapt to
 * different hardware without a rebuild.
 */
@ConfigurationProperties(prefix = "prometheus")
public class PrometheusProperties {

    /** Base URL of the Prometheus HTTP API, e.g. http://prometheus:9090 */
    private String url = "http://prometheus:9090";

    private Queries queries = new Queries();

    /** Physical disks to report, mapped to their node-exporter mountpoints. */
    private List<DiskMapping> disks = List.of();

    public static class Queries {
        private String cpuUsagePercent =
                "100 - (avg(rate(node_cpu_seconds_total{mode=\"idle\"}[1m])) * 100)";
        private String cpuTempCelsius =
                "node_hwmon_temp_celsius{chip=\"platform_coretemp_0\",sensor=\"temp1\"}";
        private String memoryUsedBytes =
                "node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes";
        private String memoryTotalBytes = "node_memory_MemTotal_bytes";
        private String networkRxBytesPerSec =
                "sum(rate(node_network_receive_bytes_total{device!~\"lo|veth.*|cni.*|flannel.*|docker.*\"}[1m]))";
        private String networkTxBytesPerSec =
                "sum(rate(node_network_transmit_bytes_total{device!~\"lo|veth.*|cni.*|flannel.*|docker.*\"}[1m]))";

        public String getCpuUsagePercent() { return cpuUsagePercent; }
        public void setCpuUsagePercent(String v) { this.cpuUsagePercent = v; }
        public String getCpuTempCelsius() { return cpuTempCelsius; }
        public void setCpuTempCelsius(String v) { this.cpuTempCelsius = v; }
        public String getMemoryUsedBytes() { return memoryUsedBytes; }
        public void setMemoryUsedBytes(String v) { this.memoryUsedBytes = v; }
        public String getMemoryTotalBytes() { return memoryTotalBytes; }
        public void setMemoryTotalBytes(String v) { this.memoryTotalBytes = v; }
        public String getNetworkRxBytesPerSec() { return networkRxBytesPerSec; }
        public void setNetworkRxBytesPerSec(String v) { this.networkRxBytesPerSec = v; }
        public String getNetworkTxBytesPerSec() { return networkTxBytesPerSec; }
        public void setNetworkTxBytesPerSec(String v) { this.networkTxBytesPerSec = v; }
    }

    public static class DiskMapping {
        /** Human label, e.g. "SSD (root)". */
        private String label;
        /** node-exporter mountpoint, e.g. "/". */
        private String mountpoint;
        /** Optional hwmon chip for this disk's temperature (e.g. "nvme_nvme0"). */
        private String tempChip;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getMountpoint() { return mountpoint; }
        public void setMountpoint(String mountpoint) { this.mountpoint = mountpoint; }
        public String getTempChip() { return tempChip; }
        public void setTempChip(String tempChip) { this.tempChip = tempChip; }
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Queries getQueries() { return queries; }
    public void setQueries(Queries queries) { this.queries = queries; }
    public List<DiskMapping> getDisks() { return disks; }
    public void setDisks(List<DiskMapping> disks) { this.disks = disks; }
}
