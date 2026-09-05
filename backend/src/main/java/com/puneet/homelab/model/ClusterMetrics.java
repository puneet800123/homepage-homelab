package com.puneet.homelab.model;

import java.util.List;

/**
 * Live cluster stats aggregated from Prometheus. Any field may be null when the
 * corresponding metric is unavailable so the frontend can render a graceful
 * "unavailable" state.
 */
public record ClusterMetrics(
        Double cpuUsagePercent,
        Double cpuTempCelsius,
        Memory memory,
        Network network,
        List<Disk> disks,
        Boolean prometheusReachable
) {
    public record Memory(
            Long usedBytes,
            Long totalBytes,
            Double usedPercent
    ) {}

    public record Network(
            Double rxBytesPerSec,
            Double txBytesPerSec
    ) {}

    public record Disk(
            String label,
            String mountpoint,
            Long usedBytes,
            Long totalBytes,
            Double usedPercent,
            Double tempCelsius
    ) {}
}
