package com.puneet.homelab.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Clean-URL routing for the static pages so visitors use /dashboard instead of
 * /dashboard.html. "/" already serves index.html via Spring Boot's static handler.
 */
@Controller
public class PageController {

    @GetMapping("/dashboard")
    public String dashboard() {
        // Forward (not redirect) so the URL stays /dashboard.
        return "forward:/dashboard.html";
    }
}
