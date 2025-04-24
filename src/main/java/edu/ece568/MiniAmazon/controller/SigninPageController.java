package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class SigninPageController {
    @GetMapping("/signin")
    public String signinPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Sign-In");
        model.addAttribute("content", "signin :: content");
        model.addAttribute("css", "signin.css");
        return "layout";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Registration");
        model.addAttribute("content", "register :: content");
        return "layout";
    }

    @GetMapping("/")
    public String test() {
        return "signin";
    }
}
