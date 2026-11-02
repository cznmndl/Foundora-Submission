package com.foundora.moderation;

import com.foundora.database.entity.Item;
import com.foundora.database.repository.ItemRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The admin's All Reports and Archive search. It runs in the database rather than the feed index because admins
 * search every status, including Pending, Rejected, Removed and archived, which the feed index doesn't hold.
 * Unlike the feed's search, it also matches the location and specific spot (admins only).
 */
@Service
public class ReportSearch {

    public static final int PAGE_SIZE = 20;

    private final ItemRepository items;

    public ReportSearch(ItemRepository items) {
        this.items = items;
    }

    public Page<Item> search(AdminReportFilter filter, int page) {
        Specification<Item> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.text() != null && !filter.text().isBlank()) {
                String pattern = "%" + escapeLike(filter.text().trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("itemName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("location")), pattern, '\\'),
                        cb.like(cb.lower(root.get("locationSpot")), pattern, '\\'),
                        cb.like(cb.lower(root.get("reporterName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("reporterEmail")), pattern, '\\')));
            }
            if (filter.status() != null) {
                where.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.type() != null) {
                where.add(cb.equal(root.get("reportType"), filter.type()));
            }
            if (filter.category() != null) {
                where.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.location() != null) {
                where.add(cb.equal(root.get("location"), filter.location()));
            }
            if (filter.archived() != null) {
                where.add(filter.archived() ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            }
            if (filter.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("dateLostFound"), filter.from()));
            }
            if (filter.to() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("dateLostFound"), filter.to()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return items.findAll(spec, PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }

    /** So a search for "100%" matches the text "100%" rather than acting as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
