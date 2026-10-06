package com.foundora.service;

import com.foundora.model.ClaimRecord;
import com.foundora.model.ClaimStatus;
import com.foundora.model.Item;
import com.foundora.model.UserProfile;
import com.foundora.repository.ClaimRecordRepository;
import com.foundora.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads {@link AdminPostDetails} for a page of posts in three queries (items, profiles, claims). Admin pages only. */
@Service
public class AdminDetailsService {

    private final ItemRepository items;
    private final ClaimRecordRepository claims;
    private final ProfileService profiles;

    public AdminDetailsService(ItemRepository items, ClaimRecordRepository claims, ProfileService profiles) {
        this.items = items;
        this.claims = claims;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public Map<UUID, AdminPostDetails> forIds(Collection<UUID> ids) {
        return ids.isEmpty() ? Map.of() : forItems(items.findAllById(ids));
    }

    @Transactional(readOnly = true)
    public Map<UUID, AdminPostDetails> forItems(Collection<Item> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        Map<String, UserProfile> reporters = profiles.byEmails(page.stream().map(Item::getReporterEmail).toList());
        Map<UUID, ClaimRecord> openClaims = claims.findByItemIdInAndStatusNot(
                        page.stream().map(Item::getId).toList(), ClaimStatus.REOPENED).stream()
                .collect(Collectors.toMap(ClaimRecord::getItemId, Function.identity(), (a, b) -> a));
        Map<UUID, AdminPostDetails> details = new HashMap<>();
        for (Item item : page) {
            details.put(item.getId(), new AdminPostDetails(item.getLocation(), item.getLocationSpot(),
                    item.getReporterEmail(), reporters.get(item.getReporterEmail().toLowerCase(Locale.ROOT)),
                    openClaims.get(item.getId())));
        }
        return details;
    }

    public AdminPostDetails forItem(Item item) {
        return forItems(List.of(item)).get(item.getId());
    }
}
