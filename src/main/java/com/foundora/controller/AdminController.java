package com.foundora.controller;

import com.foundora.model.Item;
import com.foundora.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class AdminController {

    private final ItemService itemService;

    @Autowired
    public AdminController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/admin")
    public String viewPendingItems(Model model) {
        List<Item> pendingItems = itemService.getPendingItems();
        model.addAttribute("pendingItems", pendingItems);
        return "admin";
    }

    @PostMapping("/admin/approve/{id}")
    public String approveItem(@PathVariable String id) {
        itemService.updateItemStatus(id, "APPROVED");
        return "redirect:/admin";
    }

    @PostMapping("/admin/reject/{id}")
    public String rejectItem(@PathVariable String id) {
        itemService.updateItemStatus(id, "REJECTED");
        return "redirect:/admin";
    }

    @PostMapping("/admin/delete/{id}")
    public String deleteItem(@PathVariable String id) {
        itemService.deleteItem(id);
        return "redirect:/admin";
    }
}