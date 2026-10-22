package com.foundora.moderation;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ClaimRecord;
import com.foundora.database.entity.ClaimStatus;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaimTest extends WebTestBase {

    @Autowired
    private ArchiveService archiveService;

    @Test
    void markingClaimedRecordsWhoGotItFromTheirProfile() throws Exception {
        Item item = approved("Blue tumbler");
        mvc.perform(get("/admin/reports/" + item.getId() + "/claim").with(as(ADMIN))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Mark as claimed")));

        mvc.perform(claimForm(item).param("claimantEmail", STUDENT.email())
                        .param("notes", "Described the sticker on the tumbler").with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/reports"))
                .andExpect(flash().attribute("flashSuccess", containsString("Blue tumbler")));

        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.CLAIMED);
        ClaimRecord claim = claimRepository.findAll().get(0);
        assertThat(claim.getClaimantName()).isEqualTo(STUDENT.name());
        assertThat(claim.getClaimantUid()).isEqualTo(STUDENT.uid());
        assertThat(claim.getClaimantAffiliation()).isEqualTo("BS Information Technology · 2nd year · O1A");
        assertThat(claim.getReleasedByEmail()).isEqualTo(ADMIN.email());
        assertThat(claim.getNotes()).isEqualTo("Described the sticker on the tumbler");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIMED);
    }

    @Test
    void aClaimantWhoIsNotRegisteredIsTypedIn() throws Exception {
        Item item = approved("Blue tumbler");

        mvc.perform(claimForm(item).param("claimantName", "Pedro Reyes")
                        .param("claimantAffiliation", "Grade 11 · STEM A").with(as(ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(claimRepository.findAll()).singleElement().satisfies(c -> {
            assertThat(c.getClaimantName()).isEqualTo("Pedro Reyes");
            assertThat(c.getClaimantEmail()).isNull();
        });
    }

    @Test
    void claimRecordsNeedAClaimantAndAPastReleaseTime() throws Exception {
        Item item = approved("Blue tumbler");

        mvc.perform(claimForm(item).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Enter the claimant")));
        mvc.perform(claimForm(item).param("claimantEmail", "nobody_here@dlsl.edu.ph").with(as(ADMIN)).with(csrf()))
                .andExpect(content().string(containsString("No registered user has the email")));
        mvc.perform(post("/admin/reports/" + item.getId() + "/claim").param("claimantName", "Pedro Reyes")
                        .param("claimantAffiliation", "BSIT").param("releasedAt", localNow().plusDays(1).toString())
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(content().string(containsString("can&#39;t be in the future")));
        mvc.perform(post("/admin/reports/" + item.getId() + "/claim").param("claimantName", "Pedro Reyes")
                        .param("claimantAffiliation", "BSIT").param("releasedAt", "yesterday")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Enter the release date")));

        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(claimRepository.count()).isZero();
    }

    @Test
    void theReturnedTabHidesWhoGotItFromEveryoneButAdmins() throws Exception {
        Item claimed = approved("Blue tumbler");
        claim(claimed);
        approved("Unclaimed umbrella");

        String student = html("/?show=claimed", as(STUDENT));
        assertThat(student).contains("Blue tumbler").contains("Returned to owner")
                .doesNotContain("Pedro Reyes").doesNotContain("Unclaimed umbrella")
                .doesNotContain("Verified with school ID").doesNotContain("Grade 11");
        assertThat(html("/posts/" + claimed.getId(), as(STAFF))).contains("Returned to owner")
                .doesNotContain("Pedro Reyes").doesNotContain("Verified with school ID");

        assertThat(html("/?show=claimed", as(ADMIN))).contains("Pedro Reyes")
                .contains("Verified with school ID").contains("Grade 11 · STEM A");
        assertThat(html("/posts/" + claimed.getId(), as(ADMIN))).contains("Claim record")
                .contains("Verified with school ID").contains(ADMIN.name());
    }

    @Test
    void disputesAreRecordedAndCanBeResolved() throws Exception {
        Item item = approved("Blue tumbler");
        claim(item);
        ClaimRecord claim = claimRepository.findAll().get(0);

        mvc.perform(post("/admin/claims/" + claim.getId() + "/dispute").param("note", " ").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Describe the dispute")));
        mvc.perform(post("/admin/claims/" + claim.getId() + "/dispute")
                        .param("note", "Maria says the tumbler is hers").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("disputed")));
        ClaimRecord disputed = claimRepository.findById(claim.getId()).orElseThrow();
        assertThat(disputed.getStatus()).isEqualTo(ClaimStatus.DISPUTED);
        assertThat(disputed.getDisputeNote()).isEqualTo("Maria says the tumbler is hers");
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_DISPUTED);
        assertThat(html("/posts/" + item.getId(), as(ADMIN))).contains("Maria says the tumbler is hers");

        mvc.perform(post("/admin/claims/" + claim.getId() + "/resolve").param("note", "Checked CCTV")
                        .with(as(ADMIN)).with(csrf()));
        assertThat(claimRepository.findById(claim.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.ACTIVE);
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_RESOLVED);
    }

    @Test
    void reopeningPutsTheItemBackOnTheFeedAndKeepsTheOldRecord() throws Exception {
        Item item = approved("Blue tumbler");
        claim(item);
        ClaimRecord first = claimRepository.findAll().get(0);

        mvc.perform(post("/admin/claims/" + first.getId() + "/reopen").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Say why")));
        mvc.perform(post("/admin/claims/" + first.getId() + "/reopen").param("reason", "Released to the wrong person")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("Reopened")));

        Item after = items.findById(item.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(after.getClaimedAt()).isNull();
        assertThat(claimRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.REOPENED);
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_REOPENED);
        assertThat(html("/", as(STUDENT))).contains("Blue tumbler").doesNotContain("Returned to owner");

        // It can be claimed again; both records are kept.
        claim(after);
        assertThat(claimRepository.findByItemIdOrderByCreatedAtAsc(item.getId())).hasSize(2);
    }

    @Test
    void onlyAdminsHandleClaims() throws Exception {
        Item item = approved("Blue tumbler");
        claim(item);
        ClaimRecord claim = claimRepository.findAll().get(0);

        mvc.perform(get("/admin/reports/" + item.getId() + "/claim").with(as(STUDENT))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/claims/" + claim.getId() + "/reopen").param("reason", "x").with(as(STAFF)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/people?q=juan").with(as(STUDENT))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/people?q=juan").with(as(ADMIN)))
                .andExpect(content().json("[{\"email\":\"juan_delacruz@dlsl.edu.ph\"}]"));
        mvc.perform(get("/admin/archive").with(as(STUDENT))).andExpect(status().isForbidden());
    }

    @Test
    void claimedPostsAreArchivedAfterThirtyDaysAndKeptForAdmins() throws Exception {
        Item old = approved("Old blue tumbler");
        claim(old);
        Item recent = approved("Recent umbrella");
        claim(recent);
        setClaimedAt(old, Instant.now().minus(Duration.ofDays(31)));
        setClaimedAt(recent, Instant.now().minus(Duration.ofDays(29)));
        comment(old);

        assertThat(archiveService.archiveDue()).isEqualTo(1);

        assertThat(items.findById(old.getId()).orElseThrow().getArchivedAt()).isNotNull();
        assertThat(items.findById(recent.getId()).orElseThrow().getArchivedAt()).isNull();
        assertThat(lastAction()).isEqualTo(ActivityAction.ARCHIVED);

        // Students: gone from the feed, the Claimed tab and its link.
        assertThat(html("/", as(STUDENT))).doesNotContain("Old blue tumbler").contains("Recent umbrella");
        assertThat(html("/?show=claimed", as(STUDENT))).doesNotContain("Old blue tumbler");
        mvc.perform(get("/posts/" + old.getId()).with(as(STUDENT))).andExpect(status().isNotFound());
        // The reporter can still open their own report.
        mvc.perform(get("/posts/" + old.getId()).with(as(OTHER_STUDENT))).andExpect(status().isOk());

        // Admins: the archive keeps the report, its claim record and its comments.
        assertThat(html("/admin/archive", as(ADMIN))).contains("Old blue tumbler").contains("Pedro Reyes")
                .doesNotContain("Recent umbrella");
        assertThat(html("/admin/archive?q=tumbler", as(ADMIN))).contains("Old blue tumbler");
        assertThat(html("/posts/" + old.getId(), as(ADMIN))).contains("Archived").contains("Thanks SDFO")
                .contains("Claim record");
        assertThat(html("/admin", as(ADMIN))).contains("Archived");

        // Running again doesn't archive anything twice.
        assertThat(archiveService.archiveDue()).isZero();
    }

    @Test
    void reopeningAnArchivedClaimBringsItBack() throws Exception {
        Item item = approved("Blue tumbler");
        claim(item);
        setClaimedAt(item, Instant.now().minus(Duration.ofDays(40)));
        archiveService.archiveDue();
        ClaimRecord claim = claimRepository.findAll().get(0);

        mvc.perform(post("/admin/claims/" + claim.getId() + "/reopen").param("reason", "Owner came back")
                .with(as(ADMIN)).with(csrf()));

        Item after = items.findById(item.getId()).orElseThrow();
        assertThat(after.getArchivedAt()).isNull();
        assertThat(after.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(html("/", as(STUDENT))).contains("Blue tumbler");
    }

    @Test
    void claimedItemsCanNoLongerBeApprovedDirectly() throws Exception {
        Item item = approved("Blue tumbler");
        claim(item);

        mvc.perform(post("/admin/reports/" + item.getId() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("can't be approved")));
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.CLAIMED);
    }

    private Item approved(String name) {
        return save(item(name, ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
    }

    private void claim(Item item) throws Exception {
        mvc.perform(claimForm(item).param("claimantName", "Pedro Reyes")
                        .param("claimantAffiliation", "Grade 11 · STEM A").param("notes", "Verified with school ID")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        feedIndex.rebuild();
    }

    private void comment(Item item) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/posts/" + item.getId() + "/comments").param("body", "Thanks SDFO")
                .with(as(STUDENT)).with(csrf()));
    }

    private void setClaimedAt(Item item, Instant when) {
        Item stored = items.findById(item.getId()).orElseThrow();
        stored.setClaimedAt(when);
        items.save(stored);
    }

    private static MockHttpServletRequestBuilder claimForm(Item item) {
        return post("/admin/reports/" + item.getId() + "/claim")
                .param("releasedAt", localNow().minusMinutes(5).toString());
    }

    private static LocalDateTime localNow() {
        return LocalDateTime.now(ZoneId.of("Asia/Manila")).truncatedTo(ChronoUnit.MINUTES);
    }

    private ActivityAction lastAction() {
        return activityLog.findTop10ByOrderByCreatedAtDescIdDesc().get(0).getAction();
    }
}
