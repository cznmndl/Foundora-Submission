package com.foundora.feed;

import com.foundora.dsa.CategoryIndex;
import com.foundora.dsa.DateMergeSort;
import com.foundora.dsa.InvertedIndex;
import com.foundora.dsa.PrefixTrie;
import com.foundora.dsa.SortedInsert;
import com.foundora.model.Category;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory read model of the feed: every Approved and Claimed report, without photos.
 * The database stays the source of truth; this index only makes the feed's filtering and search fast.
 *
 * <ul>
 *   <li>Built from the database at startup, sorted with {@link DateMergeSort}.</li>
 *   <li>Updated right after an admin's change commits ({@link ItemChangedEvent}), using {@link SortedInsert}.</li>
 *   <li>Rebuilt every few minutes, so edits made directly in Supabase still show up.</li>
 * </ul>
 *
 * Readers share a read lock; writers (rebuilds and single updates) take turns on {@code writeMutex}, and hold
 * the write lock only for the moment they change the structures.
 */
@Service
public class FeedIndex {

    private static final Logger log = LoggerFactory.getLogger(FeedIndex.class);

    /** Feed order: newest post first. */
    public static final Comparator<FeedPost> NEWEST_FIRST =
            Comparator.comparing(FeedPost::createdAt).reversed();

    private final ItemRepository itemRepository;
    private final Clock clock;
    private final List<String> campusLocations;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Object writeMutex = new Object();
    private Snapshot snapshot;

    /** All the index structures, swapped as one unit on a rebuild. */
    private final class Snapshot {
        final List<FeedPost> newestFirst = new ArrayList<>();
        final Map<UUID, FeedPost> byId = new HashMap<>();
        final CategoryIndex<Category, FeedPost> byCategory =
                new CategoryIndex<>(Category.class, FeedPost::category, NEWEST_FIRST);
        final InvertedIndex<UUID> search = new InvertedIndex<>();
        final PrefixTrie locations = new PrefixTrie();

        Snapshot() {
            campusLocations.forEach(place -> addLocation(locations, place));
        }

        void add(FeedPost post) {
            SortedInsert.insert(newestFirst, post, NEWEST_FIRST);
            byCategory.add(post);
            index(post);
        }

        void index(FeedPost post) {
            byId.put(post.id(), post);
            search.add(post.id(), post.itemName(), post.description(), post.location());
            addLocation(locations, post.location());
        }

        void remove(UUID id) {
            FeedPost post = byId.remove(id);
            if (post == null) {
                return;
            }
            newestFirst.removeIf(p -> p.id().equals(id));
            byCategory.remove(post.category(), p -> p.id().equals(id));
            search.remove(id);
            removeLocation(locations, post.location());
        }
    }

    public FeedIndex(ItemRepository itemRepository, Clock clock,
                     @Value("${foundora.campus-locations:}") List<String> campusLocations) {
        this.itemRepository = itemRepository;
        this.clock = clock;
        this.campusLocations = campusLocations.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
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

    /** Reloads everything from the database. On a database error the old index stays in place. */
    public void rebuild() {
        synchronized (writeMutex) {
            Snapshot next = new Snapshot();
            try {
                List<FeedPost> posts = itemRepository.findByStatusIn(ReportStatus.ON_FEED).stream()
                        .map(FeedPost::from).toList();
                // O(n log n) merge sort once; every category bucket then fills in O(n) because input is sorted.
                List<FeedPost> sorted = DateMergeSort.sort(posts, NEWEST_FIRST);
                next.newestFirst.addAll(sorted);
                next.byCategory.addAllSorted(sorted);
                sorted.forEach(next::index);
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

    /** Applies one report's change after its transaction commits. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onItemChanged(ItemChangedEvent event) {
        synchronized (writeMutex) {
            Item item = itemRepository.findById(event.itemId()).orElse(null);
            lock.writeLock().lock();
            try {
                snapshot.remove(event.itemId());
                if (item != null && ReportStatus.ON_FEED.contains(item.getStatus())) {
                    snapshot.add(FeedPost.from(item));
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public FeedPage query(FeedQuery query) {
        lock.readLock().lock();
        try {
            // Category filter: O(1) lookup in the category index instead of scanning every post.
            List<FeedPost> candidates = query.category() != null
                    ? snapshot.byCategory.get(query.category())
                    : snapshot.newestFirst;
            // Text search: posting-list intersection in the inverted index.
            Set<UUID> textMatches = query.hasText() ? snapshot.search.search(query.text()) : null;

            List<FeedPost> matches = new ArrayList<>();
            for (FeedPost post : candidates) {
                if (textMatches != null && !textMatches.contains(post.id())) {
                    continue;
                }
                if (query.type() != null && post.type() != query.type()) {
                    continue;
                }
                if (query.hideClaimed() && post.claimed()) {
                    continue;
                }
                if (query.from() != null && post.dateLostFound().isBefore(query.from())) {
                    continue;
                }
                if (query.to() != null && post.dateLostFound().isAfter(query.to())) {
                    continue;
                }
                matches.add(post);
            }
            if (query.oldestFirst()) {
                Collections.reverse(matches); // already newest first, so reversing is O(n), no re-sort
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
            // The list is newest first, so we can stop at the first post older than a week: O(k), not O(n).
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

    public List<String> suggestLocations(String prefix, int limit) {
        lock.readLock().lock();
        try {
            return snapshot.locations.withPrefix(prefix, limit);
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

    /**
     * Adds a location under every word it contains, so "gym" suggests "Main Gym" as well as "Gym lobby".
     * Each key points back to the full place name for display.
     */
    private static void addLocation(PrefixTrie trie, String place) {
        for (String key : locationKeys(place)) {
            trie.add(key, place.trim());
        }
    }

    private static void removeLocation(PrefixTrie trie, String place) {
        for (String key : locationKeys(place)) {
            trie.remove(key, place.trim());
        }
    }

    /** "Senior High Building" -> ["senior high building", "high building", "building"]. */
    private static List<String> locationKeys(String place) {
        List<String> words = InvertedIndex.tokenize(place);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            keys.add(String.join(" ", words.subList(i, words.size())));
        }
        return keys;
    }
}
