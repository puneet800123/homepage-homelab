package com.puneet.homelab.model;

import java.util.Map;

/**
 * Status + a handful of key stats for a self-hosted application, obtained by the
 * backend proxying the app's own API (keeping API keys server-side).
 */
public record AppStatus(
        String name,
        String type,
        Health health,
        Map<String, Object> stats,
        String error
) {
    public enum Health {
        OK,
        DEGRADED,
        UNREACHABLE,
        NOT_CONFIGURED
    }
}
