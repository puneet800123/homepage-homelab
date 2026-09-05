package com.puneet.homelab.controller;

import com.puneet.homelab.model.ClusterMetrics;
import com.puneet.homelab.service.PrometheusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
