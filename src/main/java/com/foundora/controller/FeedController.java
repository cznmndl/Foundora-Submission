package com.foundora.controller;

import com.foundora.feed.FeedIndex;
import com.foundora.feed.FeedPage;
import com.foundora.model.Category;
import com.foundora.service.ReportForm;
import com.foundora.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The main feed of approved reports, for every signed-in role. */
@Controller
public class FeedController {

    private final FeedIndex feed;
    private final ReportService reports;

    public FeedController(FeedIndex feed, ReportService reports) {
        this.feed = feed;
        this.reports = reports;
    }

    @GetMapping("/")
    public String feed(@RequestParam(required = false) String type,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String hideClaimed,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) Integer page,
                       Model model) {
        FeedFilters filters = FeedFilters.parse(type, category, q, from, to, hideClaimed, sort);
        addPage(model, filters, Params.page(page));
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
                            @RequestParam(required = false) Integer page,
                            Model model) {
        FeedFilters filters = FeedFilters.parse(type, category, q, from, to, hideClaimed, sort);
        addPage(model, filters, Params.page(page));
        return "fragments/posts :: page";
    }

    private void addPage(Model model, FeedFilters filters, int page) {
        FeedPage result = feed.query(filters.toQuery(page));
        model.addAttribute("filters", filters);
        model.addAttribute("feedPage", result);
    }
}
