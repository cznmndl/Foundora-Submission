package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.feed.FeedPost;
import com.foundora.model.ClaimRecord;
import com.foundora.model.ClaimStatus;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.ClaimRecordRepository;
import com.foundora.service.AdminDetailsService;
import com.foundora.service.CommentService;
import com.foundora.service.ReportAccess;
import com.foundora.service.ReportService;
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

    public PostController(ReportService reports, ReportAccess access, ActivityLogRepository activityLog,
                          ClaimRecordRepository claims, CommentService comments,
                          AdminDetailsService adminDetails) {
        this.reports = reports;
        this.access = access;
        this.activityLog = activityLog;
        this.claims = claims;
        this.comments = comments;
        this.adminDetails = adminDetails;
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user, Model model) {
        Item item = reports.findViewable(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String claimant = item.getStatus() == ReportStatus.CLAIMED
                ? claims.findFirstByItemIdAndStatusNot(item.getId(), ClaimStatus.REOPENED)
                        .map(ClaimRecord::getClaimantName).orElse(null)
                : null;
        boolean isReporter = access.isReporter(item, user);
        model.addAttribute("post", FeedPost.from(item, claimant));
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
            model.addAttribute("history", activityLog.findByItemIdOrderByCreatedAtAscIdAsc(item.getId()));
        }
        model.addAttribute("nav", "feed");
        return "post";
    }
}
