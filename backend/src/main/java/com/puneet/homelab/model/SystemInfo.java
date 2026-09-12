package com.puneet.homelab.model;

import java.util.List;

/**
 * Static hardware / system specs shown in the "System" section. Config-driven so
 * it can be updated without a rebuild.
 */
public record SystemInfo(
        String system,
        String os,
        String kubernetes,
        Cpu cpu,
        Memory memory,
        List<Disk> disks
) {
    public record Cpu(String model, Integer cores, String baseClock) {}

    public record Memory(String total, String type, String speed, String configuredSpeed) {}

    public record Disk(String label, String model, String capacity, String type, String mountpoint) {}
}
