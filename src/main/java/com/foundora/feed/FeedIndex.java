package com.foundora.feed;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.ClaimRequestStatus;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ItemStage;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.repository.ClaimRequestRepository;
import com.foundora.database.repository.ItemRepository;
import com.foundora.dsa.CategoryIndex;
import com.foundora.dsa.DateMergeSort;
import com.foundora.dsa.InvertedIndex;
import com.foundora.dsa.SortedInsert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
public class FeedIndex {

    private static final Logger log = LoggerFactory.getLogger(FeedIndex.class);

    public static final Comparator<FeedPost> NEWEST_FIRST =
            Comparator.comparing(FeedPost::createdAt).reversed();

    private final ItemRepository itemRepository;
    private final ClaimRequestRepository requestRepository;
    private final Clock clock;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object writeMutex = new Object();
    private Snapshot snapshot;

    private final class Snapshot {
        final List<FeedPost> newestFirst = new ArrayList<>();
        final Map<UUID, FeedPost> byId = new HashMap<>();
        final CategoryIndex<Category, FeedPost> byCategory =
                new CategoryIndex<>(Category.class, FeedPost::category, NEWEST_FIRST);
        final InvertedIndex<UUID> search = new InvertedIndex<>();
        final Map<UUID, String> locationById = new HashMap<>();

        void add(FeedPost post, String location) {
            SortedInsert.insert(newestFirst, post, NEWEST_FIRST);
            byCategory.add(post);
            index(post, location);
        }

        void index(FeedPost post, String location) {
            byId.put(post.id(), post);
            search.add(post.id(), post.itemName(), post.description());
            locationById.put(post.id(), location);
        }

        void remove(UUID id) {
            FeedPost post = byId.remove(id);
            if (post == null) {
                return;
            }
            newestFirst.removeIf(p -> p.id().equals(id));
            byCategory.remove(post.category(), p -> p.id().equals(id));
            search.remove(id);
            locationById.remove(id);
        }
    }

    public FeedIndex(ItemRepository itemRepository, ClaimRequestRepository requestRepository, Clock clock) {
        this.itemRepository = itemRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
        this.snapshot = new Snapshot();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        rebuild();
    }

    @Scheduled(fixedDelayString = "${foundora.feed.rebuild-interval:PT5M}",
               initialDelayString = "${foundora.feed.rebuild-interval:PT5M}")
    public void scheduledRebuild() {
        rebuild();
    }

    public void rebuild() {
        synchronized (writeMutex) {
            Snapshot next = new Snapshot();
            try {
                List<Item> onFeed = itemRepository.findByStatusIn(ReportStatus.ON_FEED).stream()
                        .filter(Item::isOnFeed).toList();
                Map<UUID, List<ClaimRequestStatus>> requests = openRequests(onFeed);
                Map<UUID, String> locations = new HashMap<>();
                List<FeedPost> posts = new ArrayList<>(onFeed.size());
                for (Item item : onFeed) {
                    ItemStage stage = ItemStage.of(item.getStatus(), requests.getOrDefault(item.getId(), List.of()));
                    posts.add(FeedPost.from(item, stage));
                    locations.put(item.getId(), item.getLocation());
                }
                List<FeedPost> sorted = DateMergeSort.sort(posts, NEWEST_FIRST);
                next.newestFirst.addAll(sorted);
                next.byCategory.addAllSorted(sorted);
                sorted.forEach(post -> next.index(post, locations.get(post.id())));
            } catch (RuntimeException e) {
                log.warn("Feed index rebuild failed; keeping the previous index", e);
                return;
            }
            lock.writeLock().lock();
            try {
                snapshot = next;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onItemChanged(ItemChangedEvent event) {
        synchronized (writeMutex) {
            Item item = itemRepository.findById(event.itemId()).orElse(null);
            ItemStage stage = item == null ? null : ItemStage.of(item.getStatus(),
                    requestRepository.findByItemIdAndStatusIn(item.getId(), ClaimRequestStatus.OPEN).stream()
                            .map(ClaimRequest::getStatus).toList());
            lock.writeLock().lock();
            try {
                snapshot.remove(event.itemId());
                if (item != null && item.isOnFeed()) {
                    snapshot.add(FeedPost.from(item, stage), item.getLocation());
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public FeedPage query(FeedQuery query) {
        lock.readLock().lock();
        try {
            List<FeedPost> candidates = query.category() != null
                    ? snapshot.byCategory.get(query.category())
                    : snapshot.newestFirst;
            Set<UUID> textMatches = query.hasText() ? snapshot.search.search(query.text()) : null;

            List<FeedPost> matches = new ArrayList<>();
            for (FeedPost post : candidates) {
                if (textMatches != null && !textMatches.contains(post.id())) {
                    continue;
                }
                if (query.type() != null && post.type() != query.type()) {
                    continue;
                }
                if (query.claimedOnly() ? !post.claimed() : (query.hideClaimed() && post.claimed())) {
                    continue;
                }
                if (query.location() != null && !query.location().equals(snapshot.locationById.get(post.id()))) {
                    continue;
                }
                if (query.from() != null && filterDate(post).isBefore(query.from())) {
                    continue;
                }
                if (query.to() != null && filterDate(post).isAfter(query.to())) {
                    continue;
                }
                matches.add(post);
            }
            if (query.oldestFirst()) {
                Collections.reverse(matches);
            }

            int start = Math.min(query.page() * FeedQuery.PAGE_SIZE, matches.size());
            int end = Math.min(start + FeedQuery.PAGE_SIZE, matches.size());
            return new FeedPage(List.copyOf(matches.subList(start, end)), query.page(), matches.size(),
                    end < matches.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    public FeedStats stats() {
        Instant weekAgo = clock.instant().minus(Duration.ofDays(7));
        lock.readLock().lock();
        try {
            int found = 0;
            int lost = 0;
            for (FeedPost post : snapshot.newestFirst) {
                if (post.createdAt().isBefore(weekAgo)) {
                    break;
                }
                if (post.type() == ReportType.FOUND) {
                    found++;
                } else {
                    lost++;
                }
            }
            return new FeedStats(found, lost, snapshot.newestFirst.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<String> suggestSearchWords(String prefix, int limit) {
        lock.readLock().lock();
        try {
            return snapshot.search.suggest(prefix, limit);
        } finally {
            lock.readLock().unlock();
        }
    }

    private LocalDate filterDate(FeedPost post) {
        return post.money() ? LocalDate.ofInstant(post.surrenderedAt(), clock.getZone()) : post.dateLostFound();
    }

    private Map<UUID, List<ClaimRequestStatus>> openRequests(List<Item> items) {
        List<UUID> approved = items.stream().filter(i -> i.getStatus() == ReportStatus.APPROVED)
                .map(Item::getId).toList();
        if (approved.isEmpty()) {
            return Map.of();
        }
        return requestRepository.findByItemIdInAndStatusIn(approved, ClaimRequestStatus.OPEN).stream()
                .collect(Collectors.groupingBy(ClaimRequest::getItemId,
                        Collectors.mapping(ClaimRequest::getStatus, Collectors.toList())));
    }
}
