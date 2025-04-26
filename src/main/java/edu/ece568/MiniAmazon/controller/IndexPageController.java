package edu.ece568.MiniAmazon.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
public class IndexPageController {
    @GetMapping("/")
    public String indexPage(Authentication authentication) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();

        // If not logged in, redirect to "/catalog"
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:" + baseUrl + "/catalog";
        }

        // TODO: Delete print
        System.out.println("************************************* Current user authorities: " + authentication.getAuthorities());

        // Redirect to "/admin" for admins, "catalog" otherwise
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return "redirect:" + baseUrl + "/admin";
        } else {
            return "redirect:" + baseUrl + "/catalog";
        }
    }
}
