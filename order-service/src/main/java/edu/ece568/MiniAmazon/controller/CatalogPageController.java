package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CatalogPageController {
    @GetMapping("/catalog")
    public String indexPage(Model model) {
        model.addAttribute("title", "Mini-Amazon");
        model.addAttribute("template", "catalog");
        model.addAttribute("fragment", "content");
        model.addAttribute("css", "catalog.css");
        model.addAttribute("js", "catalog.js");
        return "layout";
    }
}
