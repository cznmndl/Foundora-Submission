package com.foundora.reporting;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.CampusLocations;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ItemStage;
import com.foundora.database.entity.MoneyContainer;
import com.foundora.database.entity.ReportType;
import com.foundora.feed.FeedPost;
import com.foundora.database.storage.StorageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

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
        checkItemFields(form, errors);
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
        } catch (RestClientException e) {
            log.warn("Photo upload failed; the report was not saved", e);
            model.addAttribute("photoError", StorageService.UPLOAD_FAILED);
            return formPage(model);
        }
        redirect.addFlashAttribute("sentType", form.getType());
        redirect.addFlashAttribute("sentId", saved.getId());
        redirect.addFlashAttribute("sentMoney", saved.isMoney());
        return "redirect:/report/sent";
    }

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

    private void checkItemFields(ReportForm form, BindingResult errors) {
        if (form.isMoney()) {
            if (form.getMoneyAmount() == null && !errors.hasFieldErrors("moneyAmount")) {
                errors.rejectValue("moneyAmount", "required", "Enter the amount, e.g. 500.");
            }
            if (form.getMoneyContainer() == null && !errors.hasFieldErrors("moneyContainer")) {
                errors.rejectValue("moneyContainer", "required", "Choose what the money was in.");
            }
            return;
        }
        if (form.getItemName() == null || form.getItemName().isBlank()) {
            errors.rejectValue("itemName", "required", ReportForm.NAME_REQUIRED);
        }
        if (form.getDescription() == null || form.getDescription().isBlank()) {
            errors.rejectValue("description", "required", ReportForm.DESCRIPTION_REQUIRED);
        }
    }

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
        model.addAttribute("containers", MoneyContainer.values());
        model.addAttribute("today", reports.today());
        model.addAttribute("underReview", ItemStage.UNDER_REVIEW);
        model.addAttribute("nav", "report");
        return "report";
    }
}
