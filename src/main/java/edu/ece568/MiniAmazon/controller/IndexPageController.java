package edu.ece568.MiniAmazon.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexPageController {
    @GetMapping("/")
    public String indexPage(Authentication authentication) {
        // If not logged in, redirect to "/catalog"
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/catalog";
        }

        // Redirect to "/admin" for admins, "catalog" otherwise
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return "redirect:/admin";
        } else {
            return "redirect:/catalog";
        }
    }
}
