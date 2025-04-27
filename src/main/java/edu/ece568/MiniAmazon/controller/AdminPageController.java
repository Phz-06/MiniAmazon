package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminPageController {
    @GetMapping
    public String adminPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Admin Dashboard");
        model.addAttribute("template", "admin");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "admin.js");
        return "layout";
    }
}
