package com.foundora.service;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import org.springframework.stereotype.Component;

/** Who may see a report (and its photos and comments). One rule, used by the detail page and the photo endpoints. */
@Component
public class ReportAccess {

    /**
     * On the feed (Approved, or Claimed and not archived): any signed-in user.
     * Pending, Rejected and archived: the reporter and admins. Removed: admins only.
     */
    public boolean canView(Item item, FoundoraUser user) {
        if (user.isAdmin() || item.isOnFeed()) {
            return true;
        }
        return item.getStatus() != ReportStatus.REMOVED && isReporter(item, user);
    }

    public boolean isReporter(Item item, FoundoraUser user) {
        return item.getReporterEmail().equalsIgnoreCase(user.email());
    }
}
