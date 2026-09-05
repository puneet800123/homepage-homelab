package com.puneet.homelab.controller;

import com.puneet.homelab.config.ProfileProperties;
import com.puneet.homelab.model.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileProperties props;

    public ProfileController(ProfileProperties props) {
        this.props = props;
    }

    @GetMapping
    public Profile profile() {
        return props.toProfile();
    }
}
