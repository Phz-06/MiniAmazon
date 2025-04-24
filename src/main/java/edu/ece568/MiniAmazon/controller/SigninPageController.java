package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class SigninPageController {
    @GetMapping("/signin")
    public String signinPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Sign-In");
        model.addAttribute("template", "signin");
        model.addAttribute("fragment", "content");
        model.addAttribute("css", "signin.css");
        return "layout";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Registration");
        model.addAttribute("content", "register");
        model.addAttribute("fragment", "content");
        model.addAttribute("css", "register.css");
        return "layout";
    }
}
