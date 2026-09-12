package com.puneet.homelab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Shared WebClient builder for outbound calls to Prometheus and app APIs.
 *
 * <p>Redirect following is enabled because some apps (e.g. the *arr family) answer
 * API paths with a 307 redirect that must be followed to reach the JSON response.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create().followRedirect(true);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
