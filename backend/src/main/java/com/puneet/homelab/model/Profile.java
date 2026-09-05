package com.puneet.homelab.model;

import java.util.List;

/**
 * Static profile data surfaced to the frontend. Populated from configuration
 * (application.yml / ConfigMap) so it can be changed without a rebuild.
 */
public record Profile(
        String name,
        String title,
        String summary,
        Links links,
        List<Certification> certifications
) {
    public record Links(
            String linkedin,
            String github,
            String resume,
            String coverLetter
    ) {}

    public record Certification(
            String name,
            String issuer,
            String year,
            String verifyUrl
    ) {}
}
