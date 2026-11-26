package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrdersPageController {
    @GetMapping("/orders")
    public String ordersPage(Model model) {
        model.addAttribute("title", "Mini-Amazon Orders");
        model.addAttribute("template", "orders");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "orders.js");
        return "layout";
    }
}
