package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.CampusLocations;
import com.foundora.feed.FeedPost;
import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ItemStage;
import com.foundora.model.ReportType;
import com.foundora.service.InvalidImageException;
import com.foundora.service.ReportForm;
import com.foundora.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/** The report form (a full page on phones, a dialog on the desktop feed) and its confirmation. */
@Controller
public class ReportController {

    private final ReportService reports;
    private final CampusLocations campusLocations;

    public ReportController(ReportService reports, CampusLocations campusLocations) {
        this.reports = reports;
        this.campusLocations = campusLocations;
    }

    @GetMapping("/report")
    public String form(@RequestParam(required = false) String type, Model model) {
        if (!model.containsAttribute("reportForm")) {
            ReportForm form = new ReportForm();
            form.setType(ReportType.fromSlug(type));
            model.addAttribute("reportForm", form);
        }
        return formPage(model);
    }

    @PostMapping("/report")
    public String submit(@AuthenticationPrincipal FoundoraUser user,
                         @Valid @ModelAttribute("reportForm") ReportForm form, BindingResult errors,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         Model model, RedirectAttributes redirect) {
        if (form.getDateLostFound() != null && form.getDateLostFound().isAfter(reports.today())) {
            errors.rejectValue("dateLostFound", "future", "The date can’t be in the future.");
        }
        checkLocation(form, errors);
        if (errors.hasErrors()) {
            if (photo != null && !photo.isEmpty()) {
                model.addAttribute("photoError", "Please choose the photo again.");
            }
            return formPage(model);
        }
        Item saved;
        try {
            saved = reports.submit(user, form, photo);
        } catch (InvalidImageException e) {
            model.addAttribute("photoError", e.getMessage());
            return formPage(model);
        }
        redirect.addFlashAttribute("sentType", form.getType());
        redirect.addFlashAttribute("sentId", saved.getId());
        return "redirect:/report/sent";
    }

    /** The confirmation: the reporter's own card as it will look on the board, waiting "Under review". */
    @GetMapping("/report/sent")
    public String sent(@AuthenticationPrincipal FoundoraUser user, Model model) {
        if (!model.containsAttribute("sentType")) {
            return "redirect:/my-reports";
        }
        Object id = model.getAttribute("sentId");
        if (id instanceof UUID itemId) {
            reports.findViewable(itemId, user)
                    .ifPresent(item -> model.addAttribute("sentPost", FeedPost.from(item)));
        }
        model.addAttribute("nav", "report");
        return "report-sent";
    }

    /** The place must come from the list; the specific spot is always required, and says where when it's "Other". */
    private void checkLocation(ReportForm form, BindingResult errors) {
        if (form.getLocation() != null && !form.getLocation().isBlank()
                && !campusLocations.contains(form.getLocation())) {
            errors.rejectValue("location", "unknown", "Choose a place from the list, or “Other”.");
        }
        if (form.getLocationSpot() == null || form.getLocationSpot().isBlank()) {
            String message = CampusLocations.OTHER.equals(form.getLocation())
                    ? "You chose “Other”, so describe where it was."
                    : "Describe the specific spot.";
            errors.rejectValue("locationSpot", "required", message);
        }
    }

    private String formPage(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("today", reports.today());
        model.addAttribute("underReview", ItemStage.UNDER_REVIEW);
        model.addAttribute("nav", "report");
        return "report";
    }
}
