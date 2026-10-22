package com.foundora.reporting;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.ItemRepository;
import com.foundora.database.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Submitting reports and reading them back for their reporter. */
@Service
public class ReportService {

    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ImageProcessor imageProcessor;
    private final StorageService storage;
    private final ReportAccess access;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ReportService(ItemRepository items, ActivityLogRepository activityLog, ImageProcessor imageProcessor,
                         StorageService storage, ReportAccess access, TransactionTemplate transaction, Clock clock) {
        this.items = items;
        this.activityLog = activityLog;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.access = access;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** Today in the school's time zone (Asia/Manila), so a report filed just after midnight isn't "in the future". */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Saves a new Pending report from {@code reporter}. The photo, if any, is checked and re-encoded first
     * ({@link InvalidImageException} if it's refused); if saving then fails, the uploaded photo is deleted.
     * Money reports keep no photo, name or description: only the private money details.
     */
    public Item submit(FoundoraUser reporter, ReportForm form, MultipartFile photo) {
        boolean money = form.isMoney();
        String photoPath = null;
        if (!money && photo != null && !photo.isEmpty()) {
            photoPath = storage.storePhoto(imageProcessor.toJpeg(photo));
        }
        String storedPath = photoPath;
        try {
            return transaction.execute(status -> {
                Instant now = clock.instant();
                Item item = new Item();
                item.setReportType(form.getType());
                item.setItemName(money ? Item.MONEY_NAME : form.getItemName());
                item.setDescription(money ? "" : form.getDescription());
                item.setCategory(form.getCategory());
                if (money) {
                    item.setMoneyAmount(form.getMoneyAmount());
                    item.setMoneyDenominations(form.getMoneyDenominations());
                    item.setMoneyContainer(form.getMoneyContainer());
                    item.setTimeLostFound(form.getTimeLostFound());
                }
                item.setLocation(form.getLocation());
                item.setLocationSpot(form.getLocationSpot());
                item.setPrivateDetails(form.getPrivateDetails());
                item.setDateLostFound(form.getDateLostFound());
                item.setImagePath(storedPath);
                item.setStatus(ReportStatus.PENDING);
                item.setReporterUid(reporter.uid());
                item.setReporterName(reporter.name());
                item.setReporterEmail(reporter.email());
                item.setCreatedAt(now);
                item.setUpdatedAt(now);
                Item saved = items.save(item);
                activityLog.save(new ActivityLogEntry(saved, ActivityAction.SUBMITTED,
                        reporter.email(), reporter.name(), null, now));
                return saved;
            });
        } catch (RuntimeException e) {
            if (storedPath != null) {
                storage.deletePhotoQuietly(storedPath);
            }
            throw e;
        }
    }

    public List<Item> reportsBy(FoundoraUser reporter) {
        return items.findByReporterEmailOrderByCreatedAtDesc(reporter.email());
    }

    /** The report if this user may see it; empty both when it doesn't exist and when it's not theirs to see. */
    public Optional<Item> findViewable(UUID id, FoundoraUser user) {
        return items.findById(id).filter(item -> access.canView(item, user));
    }
}
