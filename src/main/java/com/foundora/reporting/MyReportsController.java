package com.foundora.reporting;

import com.foundora.auth.FoundoraUser;
import com.foundora.moderation.ClaimRequestService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The signed-in user's own reports, in every status, and their claim requests. Replaces notifications, which are out of scope. */
@Controller
public class MyReportsController {

    private final ReportService reports;
    private final ClaimRequestService claimRequests;

    public MyReportsController(ReportService reports, ClaimRequestService claimRequests) {
        this.reports = reports;
        this.claimRequests = claimRequests;
    }

    @GetMapping("/my-reports")
    public String myReports(@AuthenticationPrincipal FoundoraUser user, Model model) {
        model.addAttribute("reports", reports.reportsBy(user));
        model.addAttribute("myClaims", claimRequests.allMine(user));
        model.addAttribute("nav", "mine");
        return "my-reports";
    }
}
