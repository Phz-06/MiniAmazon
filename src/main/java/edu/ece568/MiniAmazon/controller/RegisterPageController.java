package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegisterPageController {
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Registration");
        model.addAttribute("content", "register");
        model.addAttribute("fragment", "content");
        return "layout";
    }
}
