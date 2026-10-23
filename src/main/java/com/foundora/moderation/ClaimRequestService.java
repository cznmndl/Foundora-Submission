package com.foundora.moderation;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.ProfileService;
import com.foundora.config.Formats;
import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.ClaimRequestStatus;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ItemStage;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.repository.ActivityLogRepository;
import com.foundora.database.repository.ClaimRequestRepository;
import com.foundora.database.repository.ItemRepository;
import com.foundora.database.storage.StorageService;
import com.foundora.feed.ItemChangedEvent;
import com.foundora.reporting.ImageProcessor;
import com.foundora.reporting.InvalidImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClaimRequestService {

    public static final String SYSTEM_EMAIL = ArchiveService.SYSTEM_EMAIL;
    public static final String SYSTEM_NAME = ArchiveService.SYSTEM_NAME;

    private static final Logger log = LoggerFactory.getLogger(ClaimRequestService.class);

    private final ClaimRequestRepository requests;
    private final ItemRepository items;
    private final ActivityLogRepository activityLog;
    private final ProfileService profiles;
    private final ImageProcessor imageProcessor;
    private final StorageService storage;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final Formats formats;
    private final Clock clock;
    private final Duration passValidFor;

    public record QueueEntry(Item item, List<ClaimRequest> openRequests) {
        public ClaimRequest oldest() {
            return openRequests.get(0);
        }

        public Optional<ClaimRequest> verified() {
            return openRequests.stream().filter(r -> r.getStatus() == ClaimRequestStatus.VERIFIED).findFirst();
        }
    }

    public record MyRequest(ClaimRequest request, Item item) {
    }

    public ClaimRequestService(ClaimRequestRepository requests, ItemRepository items,
                               ActivityLogRepository activityLog, ProfileService profiles,
                               ImageProcessor imageProcessor, StorageService storage,
                               ApplicationEventPublisher events, TransactionTemplate transaction, Formats formats,
                               Clock clock, @Value("${foundora.claims.pass-valid-for:P7D}") Duration passValidFor) {
        this.requests = requests;
        this.items = items;
        this.activityLog = activityLog;
        this.profiles = profiles;
        this.imageProcessor = imageProcessor;
        this.storage = storage;
        this.events = events;
        this.transaction = transaction;
        this.formats = formats;
        this.clock = clock;
        this.passValidFor = passValidFor;
    }

    public Duration passValidFor() {
        return passValidFor;
    }

    @Transactional(readOnly = true)
    public boolean canRequest(Item item, FoundoraUser user) {
        return claimable(item) && !isReporter(item, user)
                && requests.findByItemIdAndRequesterEmailIgnoreCaseOrderByCreatedAtDesc(item.getId(), user.email())
                        .stream().noneMatch(ClaimRequest::isOpen);
    }

    @Transactional(readOnly = true)
    public List<ClaimRequest> mine(Item item, FoundoraUser user) {
        return requests.findByItemIdAndRequesterEmailIgnoreCaseOrderByCreatedAtDesc(item.getId(), user.email());
    }

    @Transactional(readOnly = true)
    public List<ClaimRequest> forItem(UUID itemId) {
        return requests.findByItemIdOrderByCreatedAtAsc(itemId);
    }

    @Transactional(readOnly = true)
    public List<MyRequest> allMine(FoundoraUser user) {
        List<ClaimRequest> mine = requests.findByRequesterEmailIgnoreCaseOrderByCreatedAtDesc(user.email());
        Map<UUID, Item> byId = items.findAllById(mine.stream().map(ClaimRequest::getItemId).distinct().toList())
                .stream().collect(Collectors.toMap(Item::getId, i -> i));
        return mine.stream().filter(r -> byId.containsKey(r.getItemId()))
                .map(r -> new MyRequest(r, byId.get(r.getItemId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<QueueEntry> queue() {
        Map<UUID, List<ClaimRequest>> byItem = requests.findByStatusInOrderByCreatedAtAsc(ClaimRequestStatus.OPEN)
                .stream().collect(Collectors.groupingBy(ClaimRequest::getItemId, LinkedHashMap::new,
                        Collectors.toList()));
        Map<UUID, Item> itemsById = items.findAllById(byItem.keySet()).stream()
                .collect(Collectors.toMap(Item::getId, i -> i));
        return byItem.entrySet().stream().filter(e -> itemsById.containsKey(e.getKey()))
                .map(e -> new QueueEntry(itemsById.get(e.getKey()), e.getValue())).toList();
    }

    @Transactional(readOnly = true)
    public ItemStage stage(Item item) {
        return ItemStage.of(item.getStatus(), requests.findByItemIdAndStatusIn(item.getId(), ClaimRequestStatus.OPEN)
                .stream().map(ClaimRequest::getStatus).toList());
    }

    @Transactional(readOnly = true)
    public Optional<ClaimRequest> findPhotoViewable(UUID id, FoundoraUser user) {
        return requests.findById(id).filter(ClaimRequest::hasPhoto)
                .filter(r -> user.isAdmin() || r.isRequester(user.email()));
    }

    public ClaimRequest request(UUID itemId, FoundoraUser user, String proofText, MultipartFile photo) {
        return request(itemId, user, proofText, photo, MoneyClaimInput.EMPTY);
    }

    public ClaimRequest request(UUID itemId, FoundoraUser user, String proofText, MultipartFile photo,
                                MoneyClaimInput moneyInput) {
        Item item = items.findById(itemId).filter(Item::isOnFeed)
                .orElseThrow(() -> new ClaimRequestException("That item isn't available."));
        if (!claimable(item)) {
            throw new ClaimRequestException(item.getReportType() == ReportType.LOST
                    ? "This is a lost-item report. If you found it, bring it to the SDFO office."
                    : "This item has already been returned.");
        }
        if (isReporter(item, user)) {
            throw new ClaimRequestException("You reported this item, so you can't claim it.");
        }
        String proof = proofText == null ? "" : proofText.strip();
        MoneyClaim money = item.isMoney() ? MoneyClaim.parse(moneyInput, clock) : null;
        if (money == null && proof.length() < ClaimRequest.MIN_PROOF) {
            throw new ClaimRequestException("Tell SDFO how you know it's yours: where and when you lost it, "
                    + "and anything only the owner would know.");
        }
        if (proof.length() > ClaimRequest.MAX_PROOF) {
            throw new ClaimRequestException("Keep it to " + ClaimRequest.MAX_PROOF + " characters or fewer.");
        }
        if (mine(item, user).stream().anyMatch(ClaimRequest::isOpen)) {
            throw new ClaimRequestException("You've already asked to claim this item. Check its status below.");
        }
        String affiliation = profiles.find(user).map(p -> p.summary().isBlank() ? null : p.summary()).orElse(null);
        Instant now = clock.instant();
        boolean hasPhoto = money == null && photo != null && !photo.isEmpty();
        String photoPath = hasPhoto ? storage.storePhoto(StorageService.CLAIMS, imageProcessor.toJpeg(photo)) : null;
        try {
            return transaction.execute(status -> {
                ClaimRequest request = new ClaimRequest(item.getId(), user.uid(), user.email(), user.name(),
                        affiliation, proof, photoPath, now);
                if (money != null) {
                    request.describeMoney(money.amount(), money.denominations(), money.where(), money.lostOn(),
                            money.lostTime(), money.container());
                }
                ClaimRequest saved = requests.save(request);
                activityLog.save(new ActivityLogEntry(item, ActivityAction.CLAIM_REQUESTED, user.email(),
                        user.name(), null, now));
                events.publishEvent(new ItemChangedEvent(item.getId()));
                return saved;
            });
        } catch (RuntimeException e) {
            if (photoPath != null) {
                storage.deletePhotoQuietly(photoPath);
            }
            throw e;
        }
    }

    @Transactional
    public ClaimRequest reply(UUID requestId, FoundoraUser user, String text) {
        ClaimRequest request = own(requestId, user);
        if (request.getStatus() != ClaimRequestStatus.NEEDS_INFO) {
            throw new ClaimRequestException("SDFO hasn't asked you anything about this request.");
        }
        String reply = text == null ? "" : text.strip();
        if (reply.isEmpty()) {
            throw new ClaimRequestException("Write your answer to SDFO's question.");
        }
        if (reply.length() > ClaimRequest.MAX_REPLY) {
            throw new ClaimRequestException("Keep it to " + ClaimRequest.MAX_REPLY + " characters or fewer.");
        }
        request.reply(reply, clock.instant());
        return requests.save(request);
    }

    @Transactional
    public ClaimRequest withdraw(UUID requestId, FoundoraUser user) {
        ClaimRequest request = own(requestId, user);
        if (!request.isOpen()) {
            throw new ClaimRequestException("This request is " + request.getStatus().label().toLowerCase()
                    + ", so there's nothing to withdraw.");
        }
        Instant now = clock.instant();
        request.end(ClaimRequestStatus.WITHDRAWN, now);
        requests.save(request);
        log(request, ActivityAction.CLAIM_WITHDRAWN, user.email(), user.name(), null, now);
        return request;
    }

    @Transactional
    public ClaimRequest verify(UUID requestId, FoundoraUser admin) {
        ClaimRequest request = find(requestId);
        if (request.getStatus() != ClaimRequestStatus.PENDING && request.getStatus() != ClaimRequestStatus.NEEDS_INFO) {
            throw new ClaimRequestException("This request is " + request.getStatus().label().toLowerCase()
                    + ", so it can't be verified.");
        }
        Item item = item(request);
        if (item.getStatus() != ReportStatus.APPROVED) {
            throw new ClaimRequestException("\"" + item.getItemName() + "\" is " + item.getStatus().label()
                    + ", so a claim on it can't be verified.");
        }
        Optional<ClaimRequest> other = requests.findByItemIdAndStatusIn(item.getId(),
                List.of(ClaimRequestStatus.VERIFIED)).stream().findFirst();
        if (other.isPresent()) {
            throw new ClaimRequestException(other.get().getRequesterName() + "'s request is already verified. "
                    + "Deny it first if this person is the real owner.");
        }
        Instant now = clock.instant();
        Instant expires = now.plus(passValidFor);
        request.verify(admin.email(), admin.name(), now, expires);
        requests.save(request);
        log(request, ActivityAction.CLAIM_VERIFIED, admin.email(), admin.name(),
                "Pickup pass for " + request.getRequesterName() + " until " + formats.dateTime(expires), now);
        return request;
    }

    @Transactional
    public ClaimRequest deny(UUID requestId, FoundoraUser admin, String reason) {
        ClaimRequest request = find(requestId);
        if (!request.isOpen()) {
            throw new ClaimRequestException("This request is " + request.getStatus().label().toLowerCase()
                    + ", so it can't be denied.");
        }
        String text = requiredNote(reason, "Enter a reason so the person knows why it was denied.");
        Instant now = clock.instant();
        request.review(ClaimRequestStatus.DENIED, text, admin.email(), admin.name(), now);
        requests.save(request);
        log(request, ActivityAction.CLAIM_DENIED, admin.email(), admin.name(),
                request.getRequesterName() + ": " + text, now);
        return request;
    }

    @Transactional
    public ClaimRequest askForInfo(UUID requestId, FoundoraUser admin, String question) {
        ClaimRequest request = find(requestId);
        if (request.getStatus() != ClaimRequestStatus.PENDING && request.getStatus() != ClaimRequestStatus.NEEDS_INFO) {
            throw new ClaimRequestException("This request is " + request.getStatus().label().toLowerCase()
                    + ", so you can't ask about it.");
        }
        String text = requiredNote(question, "Write the question for the person.");
        Instant now = clock.instant();
        request.review(ClaimRequestStatus.NEEDS_INFO, text, admin.email(), admin.name(), now);
        requests.save(request);
        log(request, ActivityAction.CLAIM_INFO_REQUESTED, admin.email(), admin.name(),
                request.getRequesterName() + ": " + text, now);
        return request;
    }

    @Transactional
    public ClaimRequest extendPass(UUID requestId, FoundoraUser admin) {
        ClaimRequest request = find(requestId);
        if (request.getStatus() != ClaimRequestStatus.VERIFIED) {
            throw new ClaimRequestException("Only a verified request has a pickup pass to extend.");
        }
        Instant now = clock.instant();
        Instant from = request.getExpiresAt() != null && request.getExpiresAt().isAfter(now)
                ? request.getExpiresAt() : now;
        Instant expires = from.plus(passValidFor);
        request.extendPass(expires, now);
        requests.save(request);
        log(request, ActivityAction.CLAIM_PASS_EXTENDED, admin.email(), admin.name(),
                request.getRequesterName() + "'s pass now ends " + formats.dateTime(expires), now);
        return request;
    }

    void onReleased(Item item, String claimantEmail, Instant now) {
        for (ClaimRequest r : requests.findByItemIdAndStatusIn(item.getId(), ClaimRequestStatus.OPEN)) {
            boolean theirs = claimantEmail != null && r.isRequester(claimantEmail);
            r.end(theirs ? ClaimRequestStatus.RELEASED : ClaimRequestStatus.CLOSED, now);
            requests.save(r);
        }
    }

    void closeOpen(Item item, Instant now) {
        for (ClaimRequest r : requests.findByItemIdAndStatusIn(item.getId(), ClaimRequestStatus.OPEN)) {
            r.end(ClaimRequestStatus.CLOSED, now);
            requests.save(r);
        }
    }

    @Scheduled(fixedDelayString = "${foundora.claims.archive-check-interval:PT1H}", initialDelayString = "PT2M")
    public void scheduledExpiry() {
        try {
            int expired = expireDue();
            if (expired > 0) {
                log.info("Expired {} pickup pass(es)", expired);
            }
        } catch (RuntimeException e) {
            log.warn("Expiring pickup passes failed; will try again at the next check", e);
        }
    }

    @Transactional
    public int expireDue() {
        Instant now = clock.instant();
        List<ClaimRequest> due = requests.findByStatusAndExpiresAtBefore(ClaimRequestStatus.VERIFIED, now);
        for (ClaimRequest r : due) {
            r.end(ClaimRequestStatus.EXPIRED, now);
            requests.save(r);
            log(r, ActivityAction.CLAIM_PASS_EXPIRED, SYSTEM_EMAIL, SYSTEM_NAME,
                    r.getRequesterName() + " didn't collect it in time", now);
        }
        return due.size();
    }

    private static boolean claimable(Item item) {
        return item.getReportType() == ReportType.FOUND && item.getStatus() == ReportStatus.APPROVED
                && !item.isArchived();
    }

    private static boolean isReporter(Item item, FoundoraUser user) {
        return item.getReporterEmail().equalsIgnoreCase(user.email());
    }

    private ClaimRequest find(UUID id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim request not found"));
    }

    private ClaimRequest own(UUID id, FoundoraUser user) {
        return requests.findById(id).filter(r -> r.isRequester(user.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim request not found"));
    }

    private Item item(ClaimRequest request) {
        return items.findById(request.getItemId()).orElseThrow();
    }

    private void log(ClaimRequest request, ActivityAction action, String email, String name, String note,
                     Instant now) {
        Item item = item(request);
        activityLog.save(new ActivityLogEntry(item, action, email, name, note, now));
        events.publishEvent(new ItemChangedEvent(item.getId()));
    }

    private static String requiredNote(String note, String missingMessage) {
        String text = note == null ? "" : note.strip();
        if (text.isEmpty()) {
            throw new ClaimRequestException(missingMessage);
        }
        if (text.length() > ClaimRequest.MAX_NOTE) {
            throw new ClaimRequestException("Keep it to " + ClaimRequest.MAX_NOTE + " characters or fewer.");
        }
        return text;
    }
}
