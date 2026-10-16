package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.CampusLocations;
import com.foundora.dsa.PendingQueue;
import com.foundora.feed.FeedPost;
import com.foundora.model.ActivityAction;
import com.foundora.model.Category;
import com.foundora.model.ClaimRecord;
import com.foundora.model.ClaimRequest;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.model.UserProfile;
import com.foundora.repository.ActivityLogRepository;
import com.foundora.repository.ItemRepository;
import com.foundora.service.AdminDetailsService;
import com.foundora.service.AdminPostDetails;
import com.foundora.service.AdminReportFilter;
import com.foundora.service.ArchiveService;
import com.foundora.service.ClaimForm;
import com.foundora.service.ClaimRequestException;
import com.foundora.service.ClaimRequestService;
import com.foundora.service.ModerationException;
import com.foundora.service.ModerationService;
import com.foundora.service.MoneyReports;
import com.foundora.service.ProfileService;
import com.foundora.service.ReportSearch;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Admin pages. SecurityConfig restricts everything under /admin to the ADMIN role. */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int ACTIVITY_PAGE_SIZE = 25;

    /** A pending report: its feed-post preview (which, like the feed, carries no email) plus admin-only details. */
    public record PendingReview(Item item, FeedPost post, AdminPostDetails admin) {
    }

    /** One registered user for the claimant picker. */
    public record PersonOption(String email, String label) {
    }

    private final ModerationService moderation;
    private final ReportSearch reportSearch;
    private final ActivityLogRepository activityLog;
    private final ItemRepository items;
    private final AdminDetailsService adminDetails;
    private final ProfileService profiles;
    private final ArchiveService archive;
    private final CampusLocations campusLocations;
    private final ClaimRequestService claimRequests;
    private final MoneyReports moneyReports;
    private final Clock clock;

    public AdminController(ModerationService moderation, ReportSearch reportSearch,
                           ActivityLogRepository activityLog, ItemRepository items,
                           AdminDetailsService adminDetails, ProfileService profiles, ArchiveService archive,
                           CampusLocations campusLocations, ClaimRequestService claimRequests,
                           MoneyReports moneyReports, Clock clock) {
        this.moderation = moderation;
        this.reportSearch = reportSearch;
        this.activityLog = activityLog;
        this.items = items;
        this.adminDetails = adminDetails;
        this.profiles = profiles;
        this.archive = archive;
        this.campusLocations = campusLocations;
        this.claimRequests = claimRequests;
        this.moneyReports = moneyReports;
        this.clock = clock;
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
        int queueSize = queue.size();              // before next(), which takes the front item off
        Item next = queue.next();                  // front of the queue: the oldest submission
        List<Item> waiting = queue.inOrder();      // the rest, in order
        Map<UUID, AdminPostDetails> details = adminDetails.forItems(
                next == null ? waiting : java.util.stream.Stream.concat(java.util.stream.Stream.of(next),
                        waiting.stream()).toList());
        model.addAttribute("queueSize", queueSize);
        model.addAttribute("next", next == null ? null : review(next, details));
        model.addAttribute("waiting", waiting.stream().map(i -> review(i, details)).toList());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "pending");
        return "admin/pending";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) String type,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String location,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) Integer page,
                          HttpServletRequest request, Model model) {
        AdminReportFilter filter = new AdminReportFilter(Params.text(q, 100), ReportStatus.fromSlug(status),
                ReportType.fromSlug(type), Category.fromSlug(category), Params.date(from), Params.date(to),
                campusLocations.contains(location) ? location : null, null);
        search(filter, page, request, model);
        model.addAttribute("statuses", ReportStatus.values());
        model.addAttribute("adminNav", "reports");
        return "admin/reports";
    }

    /** Archived claimed reports with their claim records, kept forever for disputes. */
    @GetMapping("/archive")
    public String archived(@RequestParam(required = false) String q,
                           @RequestParam(required = false) String type,
                           @RequestParam(required = false) String category,
                           @RequestParam(required = false) String location,
                           @RequestParam(required = false) String from,
                           @RequestParam(required = false) String to,
                           @RequestParam(required = false) Integer page,
                           HttpServletRequest request, Model model) {
        AdminReportFilter filter = new AdminReportFilter(Params.text(q, 100), null, ReportType.fromSlug(type),
                Category.fromSlug(category), Params.date(from), Params.date(to),
                campusLocations.contains(location) ? location : null, true);
        Page<Item> results = search(filter, page, request, model);
        model.addAttribute("details", adminDetails.forItems(results.getContent()));
        model.addAttribute("archiveDays", archive.archiveAfter().toDays());
        model.addAttribute("adminNav", "archive");
        return "admin/archive";
    }

    /** Claim requests: every item with open requests, the one waiting longest first, all its requests side by side. */
    @GetMapping("/claims")
    public String claims(Model model) {
        var queue = claimRequests.queue();
        model.addAttribute("queue", queue);
        model.addAttribute("details", adminDetails.forItems(queue.stream().map(ClaimRequestService.QueueEntry::item).toList()));
        model.addAttribute("passDays", claimRequests.passValidFor().toDays());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "claims");
        return "admin/claims";
    }

    /**
     * Lost money reports, which never go on the feed, beside the found money SDFO is holding, with possible
     * matches by amount and date. Sorted by date (newest first) or by amount (?sort=amount).
     */
    @GetMapping("/money")
    public String money(@RequestParam(required = false) String sort, Model model) {
        MoneyReports.Sort order = MoneyReports.Sort.fromSlug(sort);
        model.addAttribute("board", moneyReports.board(order));
        model.addAttribute("sort", order);
        model.addAttribute("matchDays", MoneyReports.MATCH_DAYS);
        model.addAttribute("matchPercent", MoneyReports.MATCH_PERCENT);
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "money");
        return "admin/money";
    }

    @PostMapping("/claim-requests/{id}/verify")
    public String verifyRequest(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                                @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return actOnRequest(() -> claimRequests.verify(id, admin), "Verified. Pickup pass sent to", returnTo, redirect);
    }

    @PostMapping("/claim-requests/{id}/deny")
    public String denyRequest(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                              @RequestParam(required = false) String reason,
                              @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return actOnRequest(() -> claimRequests.deny(id, admin, reason), "Denied the request from", returnTo, redirect);
    }

    @PostMapping("/claim-requests/{id}/ask")
    public String askRequest(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                             @RequestParam(required = false) String question,
                             @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return actOnRequest(() -> claimRequests.askForInfo(id, admin, question), "Question sent to", returnTo,
                redirect);
    }

    @PostMapping("/claim-requests/{id}/extend")
    public String extendRequest(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                                @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return actOnRequest(() -> claimRequests.extendPass(id, admin), "Extended the pickup pass of", returnTo,
                redirect);
    }

    /**
     * The activity log, filtered by category (?group=reports|claims|claim-requests) and/or one action
     * (?action=…). An action outside the chosen category is ignored; an action alone selects its category.
     */
    @GetMapping("/activity")
    public String activity(@RequestParam(required = false) String group,
                           @RequestParam(required = false) String action,
                           @RequestParam(required = false) Integer page,
                           HttpServletRequest request, Model model) {
        ActivityAction.Group category = ActivityAction.Group.fromSlug(group);
        ActivityAction filter = ActivityAction.fromSlug(action);
        if (filter != null && category != null && filter.group() != category) {
            filter = null;
        }
        if (filter != null && category == null) {
            category = filter.group();
        }
        PageRequest pageRequest = PageRequest.of(Params.page(page), ACTIVITY_PAGE_SIZE);
        var entries = filter != null ? activityLog.findByActionOrderByCreatedAtDescIdDesc(filter, pageRequest)
                : category != null ? activityLog.findByActionInOrderByCreatedAtDescIdDesc(category.actions(), pageRequest)
                : activityLog.findAllByOrderByCreatedAtDescIdDesc(pageRequest);
        model.addAttribute("entries", entries);
        addPager(model, entries, request);
        model.addAttribute("group", category);
        model.addAttribute("action", filter);
        model.addAttribute("groups", ActivityAction.Group.values());
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "activity");
        return "admin/activity";
    }

    @PostMapping("/reports/{id}/approve")
    public String approve(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                          @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.approve(id, admin).getItemName(), "Approved", returnTo, "/admin/pending",
                redirect);
    }

    @PostMapping("/reports/{id}/reject")
    public String reject(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.reject(id, admin, reason).getItemName(), "Rejected", returnTo,
                "/admin/pending", redirect);
    }

    /** The claim record form: who the item was released to, when, and any notes. */
    @GetMapping("/reports/{id}/claim")
    public String claimForm(@PathVariable UUID id, @RequestParam(required = false) String returnTo,
                            @RequestParam(value = "request", required = false) UUID requestId, Model model) {
        Item item = items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!model.containsAttribute("claimForm")) {
            ClaimForm form = new ClaimForm();
            form.setReleasedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
            // Releasing to a verified requester: fill in who they are.
            claimRequests.forItem(item.getId()).stream()
                    .filter(r -> r.getId().equals(requestId) && r.isOpen()).findFirst()
                    .ifPresent(r -> {
                        form.setClaimantEmail(r.getRequesterEmail());
                        form.setNotes("Verified claim request. Checked school ID at pickup.");
                    });
            model.addAttribute("claimForm", form);
        }
        return claimPage(item, returnTo, model);
    }

    @PostMapping("/reports/{id}/claim")
    public String claim(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                        @ModelAttribute("claimForm") ClaimForm form, BindingResult binding,
                        @RequestParam(required = false) String returnTo, Model model, RedirectAttributes redirect) {
        if (binding.hasFieldErrors("releasedAt")) {
            model.addAttribute("flashError", "Enter the release date and time, e.g. 2026-10-06 13:30.");
            Item item = items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            return claimPage(item, returnTo, model);
        }
        try {
            Item item = moderation.markClaimed(id, admin, form);
            redirect.addFlashAttribute("flashSuccess", "Marked as claimed: “" + item.getItemName() + "”.");
            return "redirect:" + Params.safeReturn(returnTo, "/admin/reports");
        } catch (ModerationException e) {
            model.addAttribute("flashError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            model.addAttribute("flashError", "Someone else changed this report at the same moment. Check it and try again.");
        }
        Item item = items.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return claimPage(item, returnTo, model);
    }

    @PostMapping("/claims/{id}/dispute")
    public String dispute(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                          @RequestParam(required = false) String note,
                          @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> claimItemName(moderation.disputeClaim(id, admin, note)), "Marked the claim as disputed for",
                returnTo, "/admin/archive", redirect);
    }

    @PostMapping("/claims/{id}/resolve")
    public String resolve(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                          @RequestParam(required = false) String note,
                          @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> claimItemName(moderation.resolveDispute(id, admin, note)), "Resolved the dispute for",
                returnTo, "/admin/archive", redirect);
    }

    @PostMapping("/claims/{id}/reopen")
    public String reopen(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.reopenClaim(id, admin, reason).getItemName(),
                "Reopened the claim. Back on the feed:", returnTo, "/admin/reports", redirect);
    }

    @PostMapping("/reports/{id}/remove")
    public String remove(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.remove(id, admin, reason).getItemName(), "Removed", returnTo,
                "/admin/reports", redirect);
    }

    /** Found money nobody claimed, dealt with under SDFO policy. The note is required and goes in the activity log. */
    @PostMapping("/reports/{id}/handle")
    public String handle(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser admin,
                         @RequestParam(required = false) String note,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        return act(() -> moderation.markHandled(id, admin, note).getItemName(), "Marked as handled by SDFO:",
                returnTo, "/admin/money", redirect);
    }

    /** Registered people matching the text, for the claimant picker's suggestions. */
    @GetMapping("/api/people")
    @ResponseBody
    public List<PersonOption> people(@RequestParam(defaultValue = "") String q) {
        String text = q.length() > 60 ? q.substring(0, 60) : q;
        return profiles.search(text).stream().map(AdminController::option).toList();
    }

    private static PersonOption option(UserProfile p) {
        String summary = p.summary();
        return new PersonOption(p.getEmail(), p.getFullName() + (summary.isBlank() ? "" : " · " + summary));
    }

    private String claimItemName(ClaimRecord claim) {
        return items.findById(claim.getItemId()).map(Item::getItemName).orElse("the report");
    }

    private String claimPage(Item item, String returnTo, Model model) {
        model.addAttribute("item", item);
        model.addAttribute("post", FeedPost.from(item));
        model.addAttribute("admin", adminDetails.forItem(item));
        model.addAttribute("returnTo", Params.safeReturn(returnTo, "/posts/" + item.getId()));
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "reports");
        return "admin/claim";
    }

    private static PendingReview review(Item item, Map<UUID, AdminPostDetails> details) {
        return new PendingReview(item, FeedPost.from(item), details.get(item.getId()));
    }

    private Page<Item> search(AdminReportFilter filter, Integer page, HttpServletRequest request, Model model) {
        Page<Item> results = reportSearch.search(filter, Params.page(page));
        model.addAttribute("filter", filter);
        model.addAttribute("results", results);
        addPager(model, results, request);
        model.addAttribute("currentUrl", Params.currentUrl(request));
        model.addAttribute("types", ReportType.values());
        model.addAttribute("categories", Category.values());
        model.addAttribute("nav", "admin");
        return results;
    }

    private static void addPager(Model model, Page<?> page, HttpServletRequest request) {
        if (page.hasPrevious()) {
            model.addAttribute("newerUrl", Params.withPage(request, page.getNumber() - 1));
        }
        if (page.hasNext()) {
            model.addAttribute("olderUrl", Params.withPage(request, page.getNumber() + 1));
        }
    }

    private String actOnRequest(Supplier<ClaimRequest> action, String doneWords, String returnTo,
                                RedirectAttributes redirect) {
        try {
            ClaimRequest request = action.get();
            redirect.addFlashAttribute("flashSuccess", doneWords + " " + request.getRequesterName() + ".");
        } catch (ClaimRequestException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("flashError",
                    "Someone else changed this request at the same moment. Check it and try again.");
        }
        return "redirect:" + Params.safeReturn(returnTo, "/admin/claims");
    }

    private String act(Supplier<String> action, String doneWords, String returnTo, String fallback,
                       RedirectAttributes redirect) {
        try {
            String itemName = action.get();
            redirect.addFlashAttribute("flashSuccess", doneWords + " “" + itemName + "”.");
        } catch (ModerationException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("flashError",
                    "Someone else changed this report at the same moment. Check it and try again.");
        }
        return "redirect:" + Params.safeReturn(returnTo, fallback);
    }
}
