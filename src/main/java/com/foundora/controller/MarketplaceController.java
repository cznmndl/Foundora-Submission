package com.foundora.controller;

import com.foundora.model.Item;
import com.foundora.service.ItemService;
import com.foundora.service.ItemFilterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class MarketplaceController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private ItemFilterService itemFilterService;

    @GetMapping("/")
    public String index(Model model, 
                        @RequestParam(required = false) String category, 
                        @RequestParam(required = false) String status) {
        List<Item> items = itemService.getApprovedItems();
        List<Item> filteredItems = itemFilterService.filterItems(items, category, status);
        model.addAttribute("items", filteredItems);
        return "index";
    }

    @GetMapping("/search")
    public String search(@RequestParam String query, Model model) {
        List<Item> items = itemService.getApprovedItems();
        List<Item> searchResults = itemFilterService.searchItems(items, query);
        model.addAttribute("items", searchResults);
        return "index";
    }
}