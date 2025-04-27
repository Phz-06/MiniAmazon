package edu.ece568.MiniAmazon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CheckoutPageController {
    @GetMapping("/checkout")
    public String checkoutPage(Model model) {
        model.addAttribute("title", "Checkout");
        model.addAttribute("template", "checkout");
        model.addAttribute("fragment", "content");
        model.addAttribute("js", "checkout.js");
        return "layout";
    }
}
