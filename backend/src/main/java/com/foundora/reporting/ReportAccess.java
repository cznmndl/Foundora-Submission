package com.foundora.reporting;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import org.springframework.stereotype.Component;

@Component
public class ReportAccess {

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
