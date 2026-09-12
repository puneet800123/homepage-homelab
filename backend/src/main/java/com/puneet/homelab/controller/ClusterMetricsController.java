package com.puneet.homelab.controller;

import com.puneet.homelab.model.ClusterHistory;
import com.puneet.homelab.model.ClusterMetrics;
import com.puneet.homelab.service.PrometheusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cluster")
public class ClusterMetricsController {

    private final PrometheusService prometheus;

    public ClusterMetricsController(PrometheusService prometheus) {
        this.prometheus = prometheus;
    }

    @GetMapping("/metrics")
    public ClusterMetrics metrics() {
        return prometheus.collect();
    }

    /**
     * Time-series history for the trend charts.
     * @param windowSec lookback window in seconds (default 1800 = 30 min)
     * @param stepSec   sample step in seconds (default 30)
     */
    @GetMapping("/history")
    public ClusterHistory history(
            @RequestParam(defaultValue = "1800") int windowSec,
            @RequestParam(defaultValue = "30") int stepSec) {
        // Clamp to sane bounds.
        int w = Math.max(300, Math.min(windowSec, 21600));
        int s = Math.max(15, Math.min(stepSec, 300));
        return prometheus.range(w, s);
    }
}
