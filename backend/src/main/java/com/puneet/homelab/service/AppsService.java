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
 */
@Service
public class AppsService {

    private static final Logger log = LoggerFactory.getLogger(AppsService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(6);

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
        String type = cfg.getType() == null ? "" : cfg.getType().toLowerCase();

        // qBittorrent needs no API key in this setup; everything else does.
        boolean keyless = "qbittorrent".equals(type);
        if (!keyless && !cfg.isConfigured()) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.NOT_CONFIGURED,
                    Map.of(), "API key not configured");
        }
        if (!cfg.isEnabled()) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.NOT_CONFIGURED,
                    Map.of(), "Disabled");
        }
        try {
            return switch (type) {
                case "sonarr"   -> arrStatus(cfg, "v3", "series", "Series");
                case "radarr"   -> arrStatus(cfg, "v3", "movie", "Movies");
                case "lidarr"   -> arrStatus(cfg, "v1", "artist", "Artists");
                case "prowlarr" -> arrStatus(cfg, "v1", "indexer", "Indexers");
                case "bazarr"   -> arrGeneric(cfg, "v3");
                case "immich"      -> immichStatus(cfg);
                case "qbittorrent" -> qbittorrentStatus(cfg);
                default -> genericHealth(cfg);
            };
        } catch (Exception e) {
            log.debug("App status error [{}]: {}", cfg.getName(), e.toString());
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    Map.of(), e.getMessage());
        }
    }

    /**
     * *arr apps ({@code /api/<ver>/system/status}) plus a count of items from a
     * collection endpoint and the download queue size.
     */
    @SuppressWarnings("unchecked")
    private AppStatus arrStatus(AppsProperties.AppConfig cfg, String apiVer,
                                String countPath, String countLabel) {
        WebClient c = client(cfg.getBaseUrl());
        Map<String, Object> sys = getJson(c, "/api/" + apiVer + "/system/status", cfg.getApiKey());
        Map<String, Object> stats = new LinkedHashMap<>();
        if (sys == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    stats, "No response from system/status");
        }
        if (sys.get("version") != null) stats.put("version", sys.get("version"));

        // Count of the primary collection (series / movie / indexer …).
        List<Object> items = getJsonArray(c, "/api/" + apiVer + "/" + countPath, cfg.getApiKey());
        if (items != null) stats.put(countLabel, items.size());

        // Download queue size (arr media apps expose /queue with totalRecords).
        Map<String, Object> queue = getJson(c, "/api/" + apiVer + "/queue", cfg.getApiKey());
        if (queue != null && queue.get("totalRecords") != null) {
            stats.put("Queue", queue.get("totalRecords"));
        }
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** Minimal *arr check (status + version only). */
    private AppStatus arrGeneric(AppsProperties.AppConfig cfg, String apiVer) {
        WebClient c = client(cfg.getBaseUrl());
        Map<String, Object> sys = getJson(c, "/api/" + apiVer + "/system/status", cfg.getApiKey());
        Map<String, Object> stats = new LinkedHashMap<>();
        if (sys == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    stats, "No response from system/status");
        }
        if (sys.get("version") != null) stats.put("version", sys.get("version"));
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** Immich: {@code GET /api/server/statistics} with the x-api-key header. */
    private AppStatus immichStatus(AppsProperties.AppConfig cfg) {
        WebClient c = client(cfg.getBaseUrl());
        Map<String, Object> st = c.get()
                .uri("/api/server/statistics")
                .header("x-api-key", cfg.getApiKey())
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .map(m -> (Map<String, Object>) m)
                .block();

        Map<String, Object> stats = new LinkedHashMap<>();
        if (st == null) {
            return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.UNREACHABLE,
                    stats, "No response from server/statistics");
        }
        if (st.get("photos") != null) stats.put("Photos", st.get("photos"));
        if (st.get("videos") != null) stats.put("Videos", st.get("videos"));
        Object usage = st.get("usage");
        if (usage instanceof Number n) stats.put("Usage", humanBytes(n.longValue()));
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** qBittorrent: version + active torrent count (unauthenticated in this setup). */
    private AppStatus qbittorrentStatus(AppsProperties.AppConfig cfg) {
        WebClient c = client(cfg.getBaseUrl());
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
        stats.put("version", version.trim());

        List<Object> torrents = getJsonArray(c, "/api/v2/torrents/info", null);
        if (torrents != null) {
            stats.put("Torrents", torrents.size());
            long downloading = torrents.stream()
                    .filter(t -> t instanceof Map)
                    .map(t -> (Map<String, Object>) t)
                    .filter(t -> String.valueOf(t.get("state")).toLowerCase().contains("dl"))
                    .count();
            stats.put("Downloading", downloading);
        }
        return new AppStatus(cfg.getName(), cfg.getType(), AppStatus.Health.OK, stats, null);
    }

    /** Fallback: a plain reachability check against the base URL. */
    private AppStatus genericHealth(AppsProperties.AppConfig cfg) {
        WebClient c = client(cfg.getBaseUrl());
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
        return new AppStatus(cfg.getName(), cfg.getType(), health, Map.of("httpStatus", ok), null);
    }

    // ------------------------------------------------------------------ helpers

    private WebClient client(String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    /** GET a JSON object; optional X-Api-Key. Returns null on any failure. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(WebClient c, String path, String apiKey) {
        return c.get()
                .uri(path)
                .headers(h -> { if (apiKey != null) h.add("X-Api-Key", apiKey); })
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .map(m -> (Map<String, Object>) m)
                .block();
    }

    /** GET a JSON array; optional X-Api-Key. Returns null on any failure. */
    @SuppressWarnings("unchecked")
    private List<Object> getJsonArray(WebClient c, String path, String apiKey) {
        return c.get()
                .uri(path)
                .headers(h -> { if (apiKey != null) h.add("X-Api-Key", apiKey); })
                .retrieve()
                .bodyToMono(List.class)
                .timeout(TIMEOUT)
                .onErrorResume(e -> Mono.empty())
                .map(l -> (List<Object>) l)
                .block();
    }

    private static String humanBytes(long bytes) {
        String[] u = {"B", "KB", "MB", "GB", "TB", "PB"};
        double v = bytes;
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(v < 10 && i > 0 ? "%.1f %s" : "%.0f %s", v, u[i]);
    }
}
