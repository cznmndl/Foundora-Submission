package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.CampusLocations;
import com.foundora.config.Params;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.MoneyContainer;
import com.foundora.moderation.AdminDetailsService;
import com.foundora.reporting.ReportForm;
import com.foundora.reporting.ReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

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
        model.addAttribute("containers", MoneyContainer.values());
        model.addAttribute("reportForm", new ReportForm());
        model.addAttribute("today", reports.today());
        model.addAttribute("nav", "feed");
        return "feed";
    }

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
