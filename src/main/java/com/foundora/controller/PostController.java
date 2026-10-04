package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.feed.FeedPost;
import com.foundora.model.Item;
import com.foundora.repository.ActivityLogRepository;
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

/** One report on its own page. Someone else's Pending or Rejected report is a 404, not a 403. */
@Controller
public class PostController {

    private final ReportService reports;
    private final ReportAccess access;
    private final ActivityLogRepository activityLog;

    public PostController(ReportService reports, ReportAccess access, ActivityLogRepository activityLog) {
        this.reports = reports;
        this.access = access;
        this.activityLog = activityLog;
    }

    @GetMapping("/posts/{id}")
    public String post(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user, Model model) {
        Item item = reports.findViewable(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("post", FeedPost.from(item));
        model.addAttribute("item", item);
        model.addAttribute("isReporter", access.isReporter(item, user));
        if (user.isAdmin()) {
            model.addAttribute("history", activityLog.findByItemIdOrderByCreatedAtAscIdAsc(item.getId()));
        }
        model.addAttribute("nav", "feed");
        return "post";
    }
}
