package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import org.springframework.stereotype.Component;

/** Who may see a report (and its photo). One rule, used by the detail page and the photo endpoint. */
@Component
public class ReportAccess {

    /**
     * Approved and Claimed: any signed-in user. Pending and Rejected: the reporter and admins.
     * Removed: admins only.
     */
    public boolean canView(Item item, FoundoraUser user) {
        if (user.isAdmin() || ReportStatus.ON_FEED.contains(item.getStatus())) {
            return true;
        }
        return item.getStatus() != ReportStatus.REMOVED && isReporter(item, user);
    }

    public boolean isReporter(Item item, FoundoraUser user) {
        return item.getReporterEmail().equalsIgnoreCase(user.email());
    }
}
