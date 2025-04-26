package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProductPageController {
    @GetMapping("/product/{id}")
    public String productPage(@PathVariable Long id, Model model) {
        model.addAttribute("title", "Product Details");
        model.addAttribute("template", "product");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "product.js");
        return "layout";
    }
}
