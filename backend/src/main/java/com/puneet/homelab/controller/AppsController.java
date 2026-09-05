package com.puneet.homelab.controller;

import com.puneet.homelab.model.AppStatus;
import com.puneet.homelab.service.AppsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
public class AppsController {

    private final AppsService apps;

    public AppsController(AppsService apps) {
        this.apps = apps;
    }

    @GetMapping
    public List<AppStatus> all() {
        return apps.all();
    }

    @GetMapping("/{name}")
    public AppStatus byName(@PathVariable String name) {
        return apps.byName(name);
    }
}
