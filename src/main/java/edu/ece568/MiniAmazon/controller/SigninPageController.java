package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SigninPageController {
    @GetMapping("/signin")
    public String signinPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Sign-In");
        model.addAttribute("template", "signin");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "login.js");
        return "layout";
    }
}
