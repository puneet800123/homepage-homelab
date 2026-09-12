package com.puneet.homelab.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads live data from the Kubernetes API using the pod's mounted ServiceAccount
 * token. Currently used to resolve a Service's NodePort by name, so the dashboard
 * links stay correct even if a NodePort changes — no hardcoded port numbers.
 *
 * <p>Results are cached briefly to avoid hammering the API on every request.
 */
@Service
public class KubernetesService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);
    private static final long CACHE_TTL_MS = 30_000;

    private static final String SA_DIR = "/var/run/secrets/kubernetes.io/serviceaccount";
    private static final Path TOKEN_PATH = Path.of(SA_DIR, "token");
    private static final Path NS_PATH = Path.of(SA_DIR, "namespace");

    private final WebClient client;
    private final boolean inCluster;
    private final String namespace;

    // cache: serviceName -> (nodePort, timestamp)
    private final Map<String, long[]> cache = new ConcurrentHashMap<>();

    public KubernetesService() {
        boolean ic = Files.exists(TOKEN_PATH);
        String ns = "default";
        WebClient c = null;
        if (ic) {
            try {
                ns = Files.readString(NS_PATH).trim();
                String host = System.getenv().getOrDefault("KUBERNETES_SERVICE_HOST", "kubernetes.default.svc");
                String port = System.getenv().getOrDefault("KUBERNETES_SERVICE_PORT", "443");
                // Read-only internal call; skip TLS verification to stay dependency-free
                // and resilient across cluster CA/cert rotations.
                HttpClient httpClient = HttpClient.create()
                        .secure(spec -> {
                            try {
                                spec.sslContext(SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build());
                            } catch (Exception e) {
                                log.warn("K8s TLS setup failed: {}", e.toString());
                            }
                        });
                c = WebClient.builder()
                        .baseUrl("https://" + host + ":" + port)
                        .clientConnector(new ReactorClientHttpConnector(httpClient))
                        .build();
            } catch (Exception e) {
                log.warn("Kubernetes API init failed: {}", e.toString());
                ic = false;
                c = null;
            }
        }
        this.inCluster = ic;
        this.namespace = ns;
        this.client = c;
        log.info("KubernetesService inCluster={} namespace={}", this.inCluster, this.namespace);
    }

    /**
     * Returns the first NodePort of the named Service, or null if not found /
     * not a NodePort / not running in-cluster.
     */
    @SuppressWarnings("unchecked")
    public Integer nodePortForService(String serviceName) {
        if (!inCluster || client == null || serviceName == null || serviceName.isBlank()) return null;

        long[] cached = cache.get(serviceName);
        if (cached != null && (System.currentTimeMillis() - cached[1]) < CACHE_TTL_MS) {
            return cached[0] == 0 ? null : (int) cached[0];
        }

        try {
            String token = Files.readString(TOKEN_PATH).trim();
            Map<String, Object> svc = client.get()
                    .uri("/api/v1/namespaces/{ns}/services/{name}", namespace, serviceName)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(TIMEOUT)
                    .onErrorResume(e -> {
                        log.debug("K8s service lookup failed [{}]: {}", serviceName, e.toString());
                        return Mono.empty();
                    })
                    .block();

            Integer np = extractNodePort(svc);
            cache.put(serviceName, new long[]{ np == null ? 0 : np, System.currentTimeMillis() });
            return np;
        } catch (Exception e) {
            log.debug("K8s service lookup error [{}]: {}", serviceName, e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer extractNodePort(Map<String, Object> svc) {
        if (svc == null) return null;
        Map<String, Object> spec = (Map<String, Object>) svc.get("spec");
        if (spec == null) return null;
        List<Map<String, Object>> ports = (List<Map<String, Object>>) spec.get("ports");
        if (ports == null) return null;
        for (Map<String, Object> p : ports) {
            Object np = p.get("nodePort");
            if (np instanceof Number n) return n.intValue();
        }
        return null;
    }
}
