package com.puneet.homelab.config;

import com.puneet.homelab.model.SystemInfo;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds {@code system.*} configuration into a {@link SystemInfo}.
 */
@ConfigurationProperties(prefix = "system")
public class HardwareProperties {

    private String system;
    private String os;
    private String kubernetes;
    private Cpu cpu = new Cpu();
    private Memory memory = new Memory();
    private List<Disk> disks = List.of();

    public SystemInfo toSystemInfo() {
        List<SystemInfo.Disk> ds = disks.stream()
                .map(d -> new SystemInfo.Disk(d.getLabel(), d.getModel(), d.getCapacity(),
                        d.getType(), d.getMountpoint()))
                .toList();
        return new SystemInfo(
                system, os, kubernetes,
                new SystemInfo.Cpu(cpu.getModel(), cpu.getCores(), cpu.getBaseClock()),
                new SystemInfo.Memory(memory.getTotal(), memory.getType(), memory.getSpeed(),
                        memory.getConfiguredSpeed()),
                ds);
    }

    public static class Cpu {
        private String model;
        private Integer cores;
        private String baseClock;
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Integer getCores() { return cores; }
        public void setCores(Integer cores) { this.cores = cores; }
        public String getBaseClock() { return baseClock; }
        public void setBaseClock(String baseClock) { this.baseClock = baseClock; }
    }

    public static class Memory {
        private String total;
        private String type;
        private String speed;
        private String configuredSpeed;
        public String getTotal() { return total; }
        public void setTotal(String total) { this.total = total; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSpeed() { return speed; }
        public void setSpeed(String speed) { this.speed = speed; }
        public String getConfiguredSpeed() { return configuredSpeed; }
        public void setConfiguredSpeed(String configuredSpeed) { this.configuredSpeed = configuredSpeed; }
    }

    public static class Disk {
        private String label;
        private String model;
        private String capacity;
        private String type;
        private String mountpoint;
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getCapacity() { return capacity; }
        public void setCapacity(String capacity) { this.capacity = capacity; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMountpoint() { return mountpoint; }
        public void setMountpoint(String mountpoint) { this.mountpoint = mountpoint; }
    }

    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    public String getOs() { return os; }
    public void setOs(String os) { this.os = os; }
    public String getKubernetes() { return kubernetes; }
    public void setKubernetes(String kubernetes) { this.kubernetes = kubernetes; }
    public Cpu getCpu() { return cpu; }
    public void setCpu(Cpu cpu) { this.cpu = cpu; }
    public Memory getMemory() { return memory; }
    public void setMemory(Memory memory) { this.memory = memory; }
    public List<Disk> getDisks() { return disks; }
    public void setDisks(List<Disk> disks) { this.disks = disks; }
}
