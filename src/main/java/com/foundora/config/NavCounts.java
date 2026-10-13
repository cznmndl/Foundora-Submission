package com.foundora.config;

import com.foundora.model.ReportStatus;
import com.foundora.model.ClaimRequestStatus;
import com.foundora.repository.ClaimRequestRepository;
import com.foundora.repository.ItemRepository;
import org.springframework.stereotype.Component;

/** Numbers shown in the navigation, read by templates as {@code ${@navCounts.pending()}} only where displayed. */
@Component("navCounts")
public class NavCounts {

    private final ItemRepository items;
    private final ClaimRequestRepository claimRequests;

    public NavCounts(ItemRepository items, ClaimRequestRepository claimRequests) {
        this.items = items;
        this.claimRequests = claimRequests;
    }

    public long pending() {
        return items.countByStatus(ReportStatus.PENDING);
    }

    /** Claim requests waiting for an admin's decision. */
    public long claims() {
        return claimRequests.countByStatus(ClaimRequestStatus.PENDING);
    }
}
