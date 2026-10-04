package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Item;
import com.foundora.service.ItemService;
import com.foundora.service.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Controller
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ItemService itemService;

    @Autowired
    private StorageService storageService;

    @RequestMapping
    public String showReportForm(Model model) {
        model.addAttribute("item", new Item());
        return "report";
    }

    @PostMapping("/submit")
    public String submitReport(@AuthenticationPrincipal FoundoraUser user,
                                @RequestParam("title") String title,
                                @RequestParam("description") String description,
                                @RequestParam("category") String category,
                                @RequestParam("location") String location,
                                @RequestParam("dateLostFound") LocalDate dateLostFound,
                                @RequestParam("status") String status,
                                @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        Item item = new Item();
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setLocation(location);
        item.setDateLostFound(dateLostFound);
        item.setStatus(status);
        // The reporter is whoever is signed in, not whatever the form claims.
        item.setReporterName(user.name());
        item.setReporterEmail(user.email());
        item.setImageUrl(storageService.uploadImage(imageFile));
        itemService.addItem(item);
        return "redirect:/"; // Redirect to the homepage after submission
    }
}
