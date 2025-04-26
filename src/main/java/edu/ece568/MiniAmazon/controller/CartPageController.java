package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartPageController {
    @GetMapping("/cart")
    public String indexPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Cart");
        model.addAttribute("template", "cart");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "cart.js");
        return "layout";
    }
}
