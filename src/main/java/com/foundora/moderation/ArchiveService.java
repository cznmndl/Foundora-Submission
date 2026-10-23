package com.foundora.moderation;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.ItemRepository;
import com.foundora.feed.ItemChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ArchiveService {

    public static final String SYSTEM_EMAIL = "system";
    public static final String SYSTEM_NAME = "Foundora (automatic)";

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final Duration archiveAfter;

    public ArchiveService(ItemRepository items, ActivityLogRepository activityLog, ApplicationEventPublisher events,
                          Clock clock, @Value("${foundora.claims.archive-after:P30D}") Duration archiveAfter) {
        this.items = items;
        this.activityLog = activityLog;
        this.events = events;
        this.clock = clock;
        this.archiveAfter = archiveAfter;
    }

    public Duration archiveAfter() {
        return archiveAfter;
    }

    @Scheduled(fixedDelayString = "${foundora.claims.archive-check-interval:PT1H}", initialDelayString = "PT1M")
    public void scheduledArchive() {
        try {
            int archived = archiveDue();
            if (archived > 0) {
                log.info("Archived {} claimed report(s)", archived);
            }
        } catch (RuntimeException e) {
            log.warn("Archiving claimed reports failed; will try again at the next check", e);
        }
    }

    @Transactional
    public int archiveDue() {
        Instant now = clock.instant();
        List<Item> due = items.findByStatusAndArchivedAtIsNullAndClaimedAtBefore(ReportStatus.CLAIMED,
                now.minus(archiveAfter));
        for (Item item : due) {
            item.setArchivedAt(now);
            item.setUpdatedAt(now);
            items.save(item);
            activityLog.save(new ActivityLogEntry(item, ActivityAction.ARCHIVED, SYSTEM_EMAIL, SYSTEM_NAME,
                    null, now));
            events.publishEvent(new ItemChangedEvent(item.getId()));
        }
        return due.size();
    }
}
