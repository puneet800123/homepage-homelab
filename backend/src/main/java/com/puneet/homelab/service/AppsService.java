package com.puneet.homelab.service;

import com.puneet.homelab.config.AppsProperties;
import com.puneet.homelab.model.AppStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates status + a few key stats for each configured self-hosted app by
 * proxying the app's own API. API keys stay in the backend; the frontend only ever
 * sees the summarised status.
 *
 * <p>v1 scope: reachability/health per app, with a couple of type-specific stat
 * fetchers wired as examples. Apps without a real API key report NOT_CONFIGURED.
 */
@Service
public class AppsService {

    private static final Logger log = LoggerFactory.getLogger(AppsService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient.Builder builder;
    private final AppsProperties props;

    public AppsService(WebClient.Builder builder, AppsProperties props) {
        this.builder = builder;
        this.props = props;
    }

    public List<AppStatus> all() {
        return props.getServices().stream().map(this::status).toList();
    }

    public AppStatus byName(String name) {
        return props.getServices().stream()
                .filter(a -> name.equalsIgnoreCase(a.getName()))
                .findFirst()
                .map(this::status)
                .orElse(new AppStatus(name, "unknown", AppStatus.Health.NOT_CONFIGURED, Map.of(),
                        "No app configured with name '" + name + "'"));
    }

    private AppStatus status(AppsProperties.AppConfig cfg) {
        if (!cfg.isConfigured()) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.NOT_CONFIGURED,
                    Map.of(), "API key not configured");
        }
        try {
            String type = cfg.getType() == null ? "" : cfg.getType().toLowerCase();
            return switch (type) {
                case "sonarr", "radarr", "lidarr", "prowlarr", "bazarr" -> arrStatus(cfg);
                case "qbittorrent" -> qbittorrentStatus(cfg);
                default -> genericHealth(cfg);
            };
        } catch (Exception e) {
            log.debug("App status error [{}]: {}", cfg.getName(), e.toString());
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    Map.of(), e.getMessage());
        }
    }

    /** *arr apps expose /api/v3/system/status with an X-Api-Key header. */
    @SuppressWarnings("unchecked")
    private AppStatus arrStatus(AppsProperties.AppConfig cfg) {
        WebClient c = builder.baseUrl(cfg.getBaseUrl()).build();
        Map<String, Object> sys = c.get()
                .uri("/api/v3/system/status")
                .header("X-Api-Key", cfg.getApiKey())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .block();

        Map<String, Object> stats = new LinkedHashMap<>();
        if (sys == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    stats, "No response from system/status");
        }
        if (sys.get("version") != null) stats.put("version", sys.get("version"));
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** qBittorrent: check the API version endpoint (auth handling is app-specific). */
    private AppStatus qbittorrentStatus(AppsProperties.AppConfig cfg) {
        WebClient c = builder.baseUrl(cfg.getBaseUrl()).build();
        String version = c.get()
                .uri("/api/v2/app/version")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .block();

        Map<String, Object> stats = new LinkedHashMap<>();
        if (version == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    stats, "No response from app/version");
        }
        stats.put("version", version);
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** Fallback: a plain reachability check against the base URL. */
    private AppStatus genericHealth(AppsProperties.AppConfig cfg) {
        WebClient c = builder.baseUrl(cfg.getBaseUrl()).build();
        Integer ok = c.get()
                .uri("/")
                .exchangeToMono(resp -> Mono.just(resp.statusCode().value()))
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .block();

        if (ok == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    Map.of(), "No response");
        }
        AppStatus.Health health = ok < 500 ? AppStatus.Health.OK : AppStatus.Health.DEGRADED;
        return new AppStatus(cfg.getName(), cfg.getType(), health,
                Map.of("httpStatus", ok), null);
    }
}
