package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The signed-in user's own reports, in every status. Replaces notifications, which are out of scope. */
@Controller
public class MyReportsController {

    private final ReportService reports;

    public MyReportsController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/my-reports")
    public String myReports(@AuthenticationPrincipal FoundoraUser user, Model model) {
        model.addAttribute("reports", reports.reportsBy(user));
        model.addAttribute("nav", "mine");
        return "my-reports";
    }
}
