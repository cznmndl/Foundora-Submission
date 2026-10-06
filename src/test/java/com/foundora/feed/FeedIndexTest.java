package com.foundora.feed;

import com.foundora.model.Category;
import com.foundora.model.ClaimRecord;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.repository.ClaimRecordRepository;
import com.foundora.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedIndexTest {

    private static final Instant NOW = Instant.parse("2026-10-04T04:00:00Z");

    private final ItemRepository repository = mock(ItemRepository.class);
    private final ClaimRecordRepository claims = mock(ClaimRecordRepository.class);
    private final List<Item> stored = new ArrayList<>();
    private final List<ClaimRecord> storedClaims = new ArrayList<>();
    private FeedIndex index;

    @BeforeEach
    void setUp() {
        when(repository.findByStatusIn(any())).thenAnswer(call -> List.copyOf(stored));
        when(claims.findByItemIdInAndStatusNot(any(), any())).thenAnswer(call -> List.copyOf(storedClaims));
        index = new FeedIndex(repository, claims, Clock.fixed(NOW, ZoneId.of("Asia/Manila")));
    }

    @Test
    void pagesNewestFirstTenAtATime() {
        for (int i = 0; i < 23; i++) {
            stored.add(item("Item " + i, ReportType.FOUND, Category.KEYS, NOW.minus(Duration.ofHours(i))));
        }
        index.rebuild();

        FeedPage first = index.query(query(null, null, 0));
        FeedPage third = index.query(query(null, null, 2));

        assertThat(first.posts()).hasSize(10).first().extracting(FeedPost::itemName).isEqualTo("Item 0");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.totalMatches()).isEqualTo(23);
        assertThat(third.posts()).extracting(FeedPost::itemName).containsExactly("Item 20", "Item 21", "Item 22");
        assertThat(third.hasMore()).isFalse();
    }

    @Test
    void combinesCategoryIndexWithTextSearch() {
        stored.add(item("Blue umbrella", ReportType.FOUND, Category.UMBRELLAS, NOW));
        stored.add(item("Blue tumbler", ReportType.FOUND, Category.TUMBLERS_BOTTLES, NOW.minusSeconds(60)));
        index.rebuild();

        assertThat(index.query(query(Category.UMBRELLAS, "blue", 0)).posts())
                .extracting(FeedPost::itemName).containsExactly("Blue umbrella");
        assertThat(index.query(query(null, "blue", 0)).totalMatches()).isEqualTo(2);
    }

    @Test
    void countsThisWeeksReportsByType() {
        stored.add(item("New found", ReportType.FOUND, Category.KEYS, NOW.minus(Duration.ofDays(1))));
        stored.add(item("New lost", ReportType.LOST, Category.KEYS, NOW.minus(Duration.ofDays(2))));
        stored.add(item("Old found", ReportType.FOUND, Category.KEYS, NOW.minus(Duration.ofDays(9))));
        index.rebuild();

        assertThat(index.stats()).isEqualTo(new FeedStats(1, 1, 3));
    }

    @Test
    void appliesSingleChangesWithoutARebuild() {
        index.rebuild();
        Item approved = item("Calculator", ReportType.FOUND, Category.SCHOOL_SUPPLIES, NOW);
        when(repository.findById(approved.getId())).thenReturn(Optional.of(approved));

        index.onItemChanged(new ItemChangedEvent(approved.getId()));
        assertThat(index.query(query(Category.SCHOOL_SUPPLIES, "calc", 0)).totalMatches()).isEqualTo(1);

        approved.setStatus(ReportStatus.REMOVED);
        index.onItemChanged(new ItemChangedEvent(approved.getId()));
        assertThat(index.query(query(null, null, 0)).totalMatches()).isZero();
        assertThat(index.suggestSearchWords("calc", 5)).isEmpty();
    }

    @Test
    void keepsTheOldIndexWhenTheDatabaseIsUnreachable() {
        stored.add(item("Keys", ReportType.LOST, Category.KEYS, NOW));
        index.rebuild();
        when(repository.findByStatusIn(any())).thenThrow(new IllegalStateException("database down"));

        index.rebuild();

        assertThat(index.query(query(null, null, 0)).totalMatches()).isEqualTo(1);
    }

    @Test
    void searchNeverMatchesTheSecretLocation() {
        Item item = item("Black wallet", ReportType.LOST, Category.BAGS_WALLETS, NOW);
        item.setLocation("Sentrum");
        item.setLocationSpot("Under the bleachers");
        stored.add(item);
        index.rebuild();

        assertThat(index.query(query(null, "wallet", 0)).totalMatches()).isEqualTo(1);
        assertThat(index.query(query(null, "sentrum", 0)).totalMatches()).isZero();
        assertThat(index.query(query(null, "bleachers", 0)).totalMatches()).isZero();
        assertThat(index.suggestSearchWords("sent", 5)).isEmpty();
        assertThat(index.suggestSearchWords("blea", 5)).isEmpty();
    }

    @Test
    void filtersByLocationForAdmins() {
        Item atOval = item("Umbrella", ReportType.FOUND, Category.UMBRELLAS, NOW);
        atOval.setLocation("Oval");
        Item atSentrum = item("Tumbler", ReportType.FOUND, Category.TUMBLERS_BOTTLES, NOW.minusSeconds(60));
        atSentrum.setLocation("Sentrum");
        stored.add(atOval);
        stored.add(atSentrum);
        index.rebuild();

        FeedQuery oval = new FeedQuery(null, null, null, null, null, false, false, 0, "Oval", false);
        assertThat(index.query(oval).posts()).extracting(FeedPost::itemName).containsExactly("Umbrella");
    }

    @Test
    void leavesArchivedPostsOffAndShowsClaimantsOnTheClaimedTab() {
        Item claimed = item("Claimed ID", ReportType.FOUND, Category.IDS_DOCUMENTS, NOW);
        claimed.setStatus(ReportStatus.CLAIMED);
        Item archived = item("Archived keys", ReportType.FOUND, Category.KEYS, NOW.minusSeconds(60));
        archived.setStatus(ReportStatus.CLAIMED);
        archived.setArchivedAt(NOW);
        Item open = item("Open bag", ReportType.LOST, Category.BAGS_WALLETS, NOW.minusSeconds(120));
        stored.add(claimed);
        stored.add(archived);
        stored.add(open);
        storedClaims.add(new ClaimRecord(claimed.getId(), null, null, "Maria Santos", "BS Psychology · 3rd year",
                NOW, "ana.cruz@dlsl.edu.ph", "Ana Cruz", null, NOW));
        index.rebuild();

        assertThat(index.query(query(null, null, 0)).posts()).extracting(FeedPost::itemName)
                .containsExactly("Claimed ID", "Open bag");
        FeedQuery claimedTab = new FeedQuery(null, null, null, null, null, false, false, 0, null, true);
        assertThat(index.query(claimedTab).posts()).singleElement()
                .satisfies(p -> assertThat(p.claimedBy()).isEqualTo("Maria Santos"));
    }

    private static FeedQuery query(Category category, String text, int page) {
        return new FeedQuery(null, category, text, null, null, false, false, page);
    }

    private static Item item(String name, ReportType type, Category category, Instant createdAt) {
        Item item = new Item();
        item.setId(UUID.randomUUID());
        item.setItemName(name);
        item.setDescription("Description of " + name);
        item.setReportType(type);
        item.setCategory(category);
        item.setLocation("Library");
        item.setDateLostFound(LocalDate.of(2026, 10, 1));
        item.setStatus(ReportStatus.APPROVED);
        item.setReporterName("Juan Dela Cruz");
        item.setReporterEmail("juan_delacruz@dlsl.edu.ph");
        item.setCreatedAt(createdAt);
        item.setUpdatedAt(createdAt);
        return item;
    }
}
