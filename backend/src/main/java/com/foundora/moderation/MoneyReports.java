package com.foundora.moderation;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class MoneyReports {

    public static final Set<ReportStatus> OPEN = EnumSet.of(ReportStatus.PENDING, ReportStatus.APPROVED);

    public static final int MATCH_DAYS = 7;
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

    private static Comparator<Entry> closestTo(MoneyDetails lost) {
        return Comparator.comparing((Entry e) -> e.money().amount().subtract(lost.amount()).abs())
                .thenComparing(e -> Math.abs(ChronoUnit.DAYS.between(lost.date(), e.money().date())));
    }
}
