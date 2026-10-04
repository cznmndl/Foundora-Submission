package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.dsa.PendingQueue;
import com.foundora.feed.FeedPost;
import com.foundora.model.ActivityAction;
import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.service.AdminReportFilter;
import com.foundora.service.ModerationException;
import com.foundora.service.ModerationService;
import com.foundora.service.ReportSearch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;
import java.util.function.Supplier;

/** Admin pages. SecurityConfig restricts everything under /admin to the ADMIN role. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int ACTIVITY_PAGE_SIZE = 25;

    /** A pending report plus its feed-post preview (which, like the feed, carries no email). */
    public record PendingReview(Item item, FeedPost post) {
        static PendingReview of(Item item) {
            return item == null ? null : new PendingReview(item, FeedPost.from(item));
        }
    }

    private final ModerationService moderation;
    private final ReportSearch reportSearch;
    private final ActivityLogRepository activityLog;

    public AdminController(ModerationService moderation, ReportSearch reportSearch,
                           ActivityLogRepository activityLog) {
        this.moderation = moderation;
        this.reportSearch = reportSearch;
        this.activityLog = activityLog;
    }

    @GetMapping
    public String overview(Model model) {
        model.addAttribute("overview", moderation.overview());
        model.addAttribute("recent", activityLog.findTop10ByOrderByCreatedAtDescIdDesc());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "overview");
        return "admin/overview";
    }

    @GetMapping("/pending")
    public String pending(Model model) {
        PendingQueue<Item> queue = moderation.pendingQueue();
        model.addAttribute("queueSize", queue.size());
        model.addAttribute("next", PendingReview.of(queue.next()));  // front of the queue: the oldest submission
        model.addAttribute("waiting", queue.inOrder().stream().map(PendingReview::of).toList()); // the rest, in order
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "pending");
        return "admin/pending";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String type,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) Integer page,
                          HttpServletRequest request, Model model) {
        AdminReportFilter filter = new AdminReportFilter(Params.text(q, 100), ReportStatus.fromSlug(status),
                ReportType.fromSlug(type), Category.fromSlug(category), Params.date(from), Params.date(to));
        model.addAttribute("filter", filter);
        Page<Item> results = reportSearch.search(filter, Params.page(page));
        model.addAttribute("results", results);
        addPager(model, results, request);
        model.addAttribute("currentUrl", Params.currentUrl(request));
        model.addAttribute("statuses", ReportStatus.values());
        model.addAttribute("types", ReportType.values());
        model.addAttribute("categories", Category.values());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "reports");
        return "admin/reports";
    }

    @GetMapping("/activity")
    public String activity(@RequestParam(required = false) String action,
                           @RequestParam(required = false) Integer page,
                           HttpServletRequest request, Model model) {
        ActivityAction filter = ActivityAction.fromSlug(action);
        PageRequest pageRequest = PageRequest.of(Params.page(page), ACTIVITY_PAGE_SIZE);
        var entries = filter == null
                ? activityLog.findAllByOrderByCreatedAtDescIdDesc(pageRequest)
                : activityLog.findByActionOrderByCreatedAtDescIdDesc(filter, pageRequest);
        model.addAttribute("entries", entries);
        addPager(model, entries, request);
        model.addAttribute("action", filter);
        model.addAttribute("actions", ActivityAction.values());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "activity");
        return "admin/activity";
    }

    @PostMapping("/reports/{id}/approve")
    public String approve(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                          @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.approve(id, admin), "Approved", returnTo, "/admin/pending", redirect);
    }

    @PostMapping("/reports/{id}/reject")
    public String reject(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.reject(id, admin, reason), "Rejected", returnTo, "/admin/pending", redirect);
    }

    @PostMapping("/reports/{id}/claim")
    public String claim(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                        @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.markClaimed(id, admin), "Marked as claimed:", returnTo, "/admin/reports",
                redirect);
    }

    @PostMapping("/reports/{id}/remove")
    public String remove(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.remove(id, admin, reason), "Removed", returnTo, "/admin/reports", redirect);
    }

    private static void addPager(Model model, Page<?> page, HttpServletRequest request) {
        if (page.hasPrevious()) {
            model.addAttribute("newerUrl", Params.withPage(request, page.getNumber() - 1));
        }
        if (page.hasNext()) {
            model.addAttribute("olderUrl", Params.withPage(request, page.getNumber() + 1));
        }
    }

    private String act(Supplier<Item> action, String doneWord, String returnTo, String fallback,
                       RedirectAttributes redirect) {
        try {
            Item item = action.get();
            redirect.addFlashAttribute("flashSuccess", doneWord + " “" + item.getItemName() + "”.");
        } catch (ModerationException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("flashError",
                    "Someone else changed this report at the same moment. Check it and try again.");
        }
        return "redirect:" + Params.safeReturn(returnTo, fallback);
    }
}
