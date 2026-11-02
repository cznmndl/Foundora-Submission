package com.foundora.moderation;

import com.foundora.database.entity.ClaimRequestStatus;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.repository.ClaimRequestRepository;
import com.foundora.database.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.util.function.LongSupplier;

@Component("navCounts")
public class NavCounts {

    private static final Logger log = LoggerFactory.getLogger(NavCounts.class);

    private final ItemRepository items;
    private final ClaimRequestRepository claimRequests;

    public NavCounts(ItemRepository items, ClaimRequestRepository claimRequests) {
        this.items = items;
        this.claimRequests = claimRequests;
    }

    public long pending() {
        return countOrZero(() -> items.countByStatus(ReportStatus.PENDING));
    }

    public long claims() {
        return countOrZero(() -> claimRequests.countByStatus(ClaimRequestStatus.PENDING));
    }

    private static long countOrZero(LongSupplier count) {
        try {
            return count.getAsLong();
        } catch (DataAccessException | TransactionException e) {
            log.debug("Navigation count unavailable", e);
            return 0;
        }
    }
}
