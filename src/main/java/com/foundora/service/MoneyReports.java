package com.foundora.service;

import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The admin's Lost money view. Lost money never goes on the feed, so this is where SDFO compares it with the
 * found money it's holding, by amount and date. Admin pages only: every entry carries the private amount.
 */
@Service
public class MoneyReports {

    /** Reports still in play: waiting for review, or approved and not yet claimed or handled. */
    public static final Set<ReportStatus> OPEN = EnumSet.of(ReportStatus.PENDING, ReportStatus.APPROVED);

    /** A possible match is found money within this many days of the lost date... */
    public static final int MATCH_DAYS = 7;
    /** ...and within this share of the lost amount, either way (people miscount cash). */
    public static final int MATCH_PERCENT = 20;

    public enum Sort {
        DATE, AMOUNT;

        public String slug() {
            return name().toLowerCase();
        }

        public static Sort fromSlug(String slug) {
            return "amount".equalsIgnoreCase(slug) ? AMOUNT : DATE;
        }
    }

    public record Entry(Item item, MoneyDetails money) {
    }

    /** A lost-money report and the found money that could be it. */
    public record LostEntry(Item item, MoneyDetails money, List<Entry> matches) {
    }

    public record Board(List<LostEntry> lost, List<Entry> found) {
    }

    private final ItemRepository items;

    public MoneyReports(ItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public Board board(Sort sort) {
        List<Entry> found = open(ReportType.FOUND, sort);
        List<LostEntry> lost = open(ReportType.LOST, sort).stream()
                .map(l -> new LostEntry(l.item(), l.money(), found.stream().filter(f -> matches(l.money(), f.money()))
                        .sorted(closestTo(l.money())).toList()))
                .toList();
        return new Board(lost, found);
    }

    /** Within {@link #MATCH_DAYS} days of each other and {@link #MATCH_PERCENT}% of the lost amount. */
    static boolean matches(MoneyDetails lost, MoneyDetails found) {
        long days = Math.abs(ChronoUnit.DAYS.between(lost.date(), found.date()));
        BigDecimal tolerance = lost.amount().multiply(BigDecimal.valueOf(MATCH_PERCENT)).movePointLeft(2);
        return days <= MATCH_DAYS && lost.amount().subtract(found.amount()).abs().compareTo(tolerance) <= 0;
    }

    private List<Entry> open(ReportType type, Sort sort) {
        Comparator<Entry> order = sort == Sort.AMOUNT
                ? Comparator.comparing((Entry e) -> e.money().amount()).reversed()
                : Comparator.comparing((Entry e) -> e.money().date()).reversed();
        return items.findByCategoryAndReportTypeAndStatusIn(Category.MONEY, type, OPEN).stream()
                .map(i -> new Entry(i, MoneyDetails.of(i)))
                .filter(e -> e.money() != null)
                .sorted(order.thenComparing(e -> e.item().getCreatedAt(), Comparator.reverseOrder()))
                .toList();
    }

    /** Closest amount first, then closest date. */
    private static Comparator<Entry> closestTo(MoneyDetails lost) {
        return Comparator.comparing((Entry e) -> e.money().amount().subtract(lost.amount()).abs())
                .thenComparing(e -> Math.abs(ChronoUnit.DAYS.between(lost.date(), e.money().date())));
    }
}
