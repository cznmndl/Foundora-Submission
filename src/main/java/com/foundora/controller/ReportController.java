package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Category;
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

/** The report form (a full page on phones, a dialog on the desktop feed) and its confirmation. */
@Controller
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
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
        if (errors.hasErrors()) {
            if (photo != null && !photo.isEmpty()) {
                model.addAttribute("photoError", "Please choose the photo again.");
            }
            return formPage(model);
        }
        try {
            reports.submit(user, form, photo);
        } catch (InvalidImageException e) {
            model.addAttribute("photoError", e.getMessage());
            return formPage(model);
        }
        redirect.addFlashAttribute("sentType", form.getType());
        return "redirect:/report/sent";
    }

    @GetMapping("/report/sent")
    public String sent(Model model) {
        if (!model.containsAttribute("sentType")) {
            return "redirect:/my-reports";
        }
        model.addAttribute("nav", "report");
        return "report-sent";
    }

    private String formPage(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("today", reports.today());
        model.addAttribute("nav", "report");
        return "report";
    }
}
