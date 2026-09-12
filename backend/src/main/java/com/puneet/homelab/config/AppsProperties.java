package com.puneet.homelab.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configures the self-hosted applications the dashboard aggregates. Each entry has
 * a base URL and an API key/token; keys are supplied via a Kubernetes Secret and
 * never leave the backend.
 */
@ConfigurationProperties(prefix = "apps")
public class AppsProperties {

    private List<AppConfig> services = List.of();

    public static class AppConfig {
        /** Unique id/name, e.g. "sonarr". */
        private String name;
        /** App family, drives which stats are fetched, e.g. "sonarr", "qbittorrent". */
        private String type;
        /** Base URL of the app, e.g. http://sonarr.default.svc.cluster.local. */
        private String baseUrl;
        /** API key / token. Empty or CHANGE_ME means "not configured". */
        private String apiKey;
        /** Whether to include this app on the dashboard. */
        private boolean enabled = true;
        /** NodePort the app is reachable on (for the clickable tile link). 0 = none. */
        private int nodePort = 0;
        /** K8s Service name to resolve the NodePort from live (preferred over nodePort). */
        private String serviceName;
        /** Grouping category shown on the dashboard, e.g. "Media", "Observability". */
        private String category = "Other";

        public boolean isConfigured() {
            return enabled
                    && baseUrl != null && !baseUrl.isBlank()
                    && apiKey != null && !apiKey.isBlank()
                    && !"CHANGE_ME".equals(apiKey);
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getNodePort() { return nodePort; }
        public void setNodePort(int nodePort) { this.nodePort = nodePort; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }

    public List<AppConfig> getServices() { return services; }
    public void setServices(List<AppConfig> services) { this.services = services; }
}
