package com.foundora.config;

import com.foundora.model.ReportStatus;
import com.foundora.repository.ItemRepository;
import org.springframework.stereotype.Component;

/** Numbers shown in the navigation, read by templates as {@code ${@navCounts.pending()}} only where displayed. */
@Component("navCounts")
public class NavCounts {

    private final ItemRepository items;

    public NavCounts(ItemRepository items) {
        this.items = items;
    }

    public long pending() {
        return items.countByStatus(ReportStatus.PENDING);
    }
}
