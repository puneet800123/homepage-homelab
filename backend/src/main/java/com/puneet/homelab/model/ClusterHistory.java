package com.puneet.homelab.model;

import java.util.List;

/**
 * Time-series history for the trend charts. Each series is a list of points the
 * frontend plots as a sparkline / area chart. Any series may be empty when the
 * corresponding data is unavailable.
 */
public record ClusterHistory(
        Long startEpochSec,
        Long endEpochSec,
        Long stepSec,
        List<Point> cpuUsagePercent,
        List<Point> memoryUsedPercent,
        List<Point> networkRxBytesPerSec,
        List<Point> networkTxBytesPerSec,
        Boolean prometheusReachable
) {
    /** A single time-series sample: epoch seconds + value. */
    public record Point(long t, double v) {}
}
