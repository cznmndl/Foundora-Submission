package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.MoneyContainer;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.ClaimRecordRepository;
import com.foundora.moderation.AdminDetailsService;
import com.foundora.claims.ClaimRequestService;
import com.foundora.reporting.ReportAccess;
import com.foundora.reporting.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * One report on its own page, with its comments. Someone else's Pending, Rejected or archived report is a 404,
 * not a 403. Non-admins get only the public {@link FeedPost} (no location, no email); the full report, its
 * location and claim records go to admins only.
 */
@Controller
public class PostController {

    private final ReportService reports;
    private final ReportAccess access;
    private final ActivityLogRepository activityLog;
    private final ClaimRecordRepository claims;
    private final CommentService comments;
    private final AdminDetailsService adminDetails;
    private final ClaimRequestService claimRequests;

    public PostController(ReportService reports, ReportAccess access, ActivityLogRepository activityLog,
                          ClaimRecordRepository claims, CommentService comments,
                          AdminDetailsService adminDetails, ClaimRequestService claimRequests) {
        this.reports = reports;
        this.access = access;
        this.activityLog = activityLog;
        this.claims = claims;
        this.comments = comments;
        this.adminDetails = adminDetails;
        this.claimRequests = claimRequests;
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user, Model model) {
        Item item = reports.findViewable(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean isReporter = access.isReporter(item, user);
        model.addAttribute("post", FeedPost.from(item, claimRequests.stage(item)));
        // Claim requests: your own (the proof is only ever shown to you and admins), and whether you can ask.
        model.addAttribute("myRequests", claimRequests.mine(item, user));
        model.addAttribute("canRequest", claimRequests.canRequest(item, user));
        model.addAttribute("passDays", claimRequests.passValidFor().toDays());
        if (item.isMoney()) {
            model.addAttribute("containers", MoneyContainer.values());
            model.addAttribute("today", reports.today());
        }
        model.addAttribute("isReporter", isReporter);
        model.addAttribute("archived", item.isArchived());
        if (isReporter || user.isAdmin()) {
            model.addAttribute("reviewReason", item.getReviewReason());
        }
        model.addAttribute("comments", comments.viewsFor(item, user));
        model.addAttribute("commentsOpen", comments.isOpen(item));
        if (user.isAdmin()) {
            model.addAttribute("item", item);
            var details = adminDetails.forItem(item);
            model.addAttribute("admin", details);
            model.addAttribute("adminDetails", java.util.Map.of(item.getId(), details));
            model.addAttribute("claimHistory", claims.findByItemIdOrderByCreatedAtAsc(item.getId()));
            model.addAttribute("claimRequests", claimRequests.forItem(item.getId()));
            model.addAttribute("history", activityLog.findByItemIdOrderByCreatedAtAscIdAsc(item.getId()));
        }
        model.addAttribute("nav", "feed");
        return "post";
    }
}
