package com.puneet.homelab.config;

import com.puneet.homelab.model.Profile;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code profile.*} configuration into a {@link Profile}.
 */
@ConfigurationProperties(prefix = "profile")
public class ProfileProperties {

    private String name;
    private String title;
    private String summary;
    private Links links = new Links();
    private List<Certification> certifications = List.of();

    public Profile toProfile() {
        List<Profile.Certification> certs = certifications.stream()
                .map(c -> new Profile.Certification(c.getName(), c.getIssuer(), c.getYear(), c.getVerifyUrl()))
                .toList();
        return new Profile(
                name,
                title,
                summary,
                new Profile.Links(links.getLinkedin(), links.getGithub(), links.getResume(), links.getCoverLetter()),
                certs
        );
    }

    public static class Links {
        private String linkedin;
        private String github;
        private String resume;
        private String coverLetter;

        public String getLinkedin() { return linkedin; }
        public void setLinkedin(String linkedin) { this.linkedin = linkedin; }
        public String getGithub() { return github; }
        public void setGithub(String github) { this.github = github; }
        public String getResume() { return resume; }
        public void setResume(String resume) { this.resume = resume; }
        public String getCoverLetter() { return coverLetter; }
        public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }
    }

    public static class Certification {
        private String name;
        private String issuer;
        private String year;
        private String verifyUrl;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }
        public String getVerifyUrl() { return verifyUrl; }
        public void setVerifyUrl(String verifyUrl) { this.verifyUrl = verifyUrl; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Links getLinks() { return links; }
    public void setLinks(Links links) { this.links = links; }
    public List<Certification> getCertifications() { return certifications; }
    public void setCertifications(List<Certification> certifications) { this.certifications = certifications; }
}
