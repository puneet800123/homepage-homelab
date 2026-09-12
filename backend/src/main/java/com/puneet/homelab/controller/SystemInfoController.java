package com.puneet.homelab.controller;

import com.puneet.homelab.config.HardwareProperties;
import com.puneet.homelab.model.SystemInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemInfoController {

    private final HardwareProperties props;

    public SystemInfoController(HardwareProperties props) {
        this.props = props;
    }

    @GetMapping("/info")
    public SystemInfo info() {
        return props.toSystemInfo();
    }
}
