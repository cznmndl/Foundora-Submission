package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.CampusLocations;
import com.foundora.feed.FeedIndex;
import com.foundora.feed.FeedPage;
import com.foundora.feed.FeedPost;
import com.foundora.model.Category;
import com.foundora.service.AdminDetailsService;
import com.foundora.service.CommentService;
import com.foundora.service.ReportForm;
import com.foundora.service.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * The main feed of approved reports, for every signed-in role. Locations are secret: only admins get the
 * location filter and the per-post {@code adminDetails}; for everyone else the location parameter is ignored.
 */
@Controller
public class FeedController {

    private final FeedIndex feed;
    private final ReportService reports;
    private final CommentService comments;
    private final AdminDetailsService adminDetails;
    private final CampusLocations campusLocations;

    public FeedController(FeedIndex feed, ReportService reports, CommentService comments,
                          AdminDetailsService adminDetails, CampusLocations campusLocations) {
        this.feed = feed;
        this.reports = reports;
        this.comments = comments;
        this.adminDetails = adminDetails;
        this.campusLocations = campusLocations;
    }

    @GetMapping("/")
    public String feed(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String hideClaimed,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) String show,
                       @RequestParam(required = false) String location,
                       @RequestParam(required = false) Integer page,
                       @AuthenticationPrincipal FoundoraUser user, Model model) {
        FeedFilters filters = FeedFilters.parse(type, category, q, from, to, hideClaimed, sort, show,
                adminLocation(user, location));
        addPage(model, filters, Params.page(page), user);
        model.addAttribute("stats", feed.stats());
        model.addAttribute("categories", Category.values());
        model.addAttribute("reportForm", new ReportForm());
        model.addAttribute("today", reports.today());
        model.addAttribute("nav", "feed");
        return "feed";
    }

    /** The next page of posts as an HTML fragment, appended by "Load more" without reloading the page. */
    @GetMapping("/feed/posts")
    public String morePosts(@RequestParam(required = false) String type,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) String from,
                            @RequestParam(required = false) String to,
                            @RequestParam(required = false) String hideClaimed,
                            @RequestParam(required = false) String sort,
                            @RequestParam(required = false) String show,
                            @RequestParam(required = false) String location,
                            @RequestParam(required = false) Integer page,
                            @AuthenticationPrincipal FoundoraUser user, Model model) {
        FeedFilters filters = FeedFilters.parse(type, category, q, from, to, hideClaimed, sort, show,
                adminLocation(user, location));
        addPage(model, filters, Params.page(page), user);
        return "fragments/posts :: page";
    }

    /** The location filter is for admins only, and only with a place from the list. */
    private String adminLocation(FoundoraUser user, String location) {
        return user.isAdmin() && campusLocations.contains(location) ? location : null;
    }

    private void addPage(Model model, FeedFilters filters, int page, FoundoraUser user) {
        FeedPage result = feed.query(filters.toQuery(page));
        List<UUID> ids = result.posts().stream().map(FeedPost::id).toList();
        model.addAttribute("filters", filters);
        model.addAttribute("feedPage", result);
        model.addAttribute("commentCounts", comments.counts(ids));
        if (user.isAdmin()) {
            model.addAttribute("adminDetails", adminDetails.forIds(ids));
        }
    }
}
