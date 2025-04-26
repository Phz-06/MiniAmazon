package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexPageController {
    @GetMapping("/")
    public String indexPage(Model model) {
        model.addAttribute("title", "Mini-Amazon");
        model.addAttribute("template", null);
        model.addAttribute("fragment", null);
        model.addAttribute("js", "index.js");
        return "layout";
    }
}
