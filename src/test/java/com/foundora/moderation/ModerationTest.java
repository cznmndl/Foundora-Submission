package com.foundora.moderation;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static com.foundora.support.TestData.minutesAgo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ModerationTest extends WebTestBase {

    @Test
    void approvingPutsTheReportOnTheFeedAndLogsWhoDidIt() throws Exception {
        Item item = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));

        mvc.perform(post(action(item, "approve")).with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/pending"))
                .andExpect(flash().attribute("flashSuccess", "Approved “Navy umbrella”."));

        Item after = reload(item);
        assertThat(after.getStatus()).isEqualTo(ReportStatus.APPROVED);
        assertThat(after.getReviewedByEmail()).isEqualTo(ADMIN.email());
        assertThat(after.getReviewedAt()).isNotNull();
        assertThat(lastLog().getAction()).isEqualTo(ActivityAction.APPROVED);
        assertThat(lastLog().getActorEmail()).isEqualTo(ADMIN.email());
        // The feed index updated after the commit, without waiting for a rebuild.
        mvc.perform(get("/").with(as(OTHER_STUDENT))).andExpect(content().string(containsString("Navy umbrella")));
    }

    @Test
    void rejectingNeedsAReasonAndDeletesThePhoto() throws Exception {
        Item pending = item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT);
        pending.setImagePath("items/photo.jpg");
        Item item = save(pending);

        mvc.perform(post(action(item, "reject")).param("reason", "  ").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Enter a reason")));
        assertThat(reload(item).getStatus()).isEqualTo(ReportStatus.PENDING);
        verify(storage, never()).deletePhotoQuietly(anyString());

        mvc.perform(post(action(item, "reject")).param("reason", "Please add a description")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("Rejected")));

        Item after = reload(item);
        assertThat(after.getStatus()).isEqualTo(ReportStatus.REJECTED);
        assertThat(after.getReviewReason()).isEqualTo("Please add a description");
        assertThat(after.getImagePath()).isNull();
        verify(storage).deletePhotoQuietly("items/photo.jpg");
        assertThat(lastLog().getReason()).isEqualTo("Please add a description");
        mvc.perform(get("/my-reports").with(as(STUDENT)))
                .andExpect(content().string(containsString("Please add a description")));
    }

    @Test
    void claimedReportsStayOnTheFeedGreyedOut() throws Exception {
        Item item = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, STUDENT));

        mvc.perform(claim(item).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("claimed")));

        Item after = reload(item);
        assertThat(after.getStatus()).isEqualTo(ReportStatus.CLAIMED);
        assertThat(after.getClaimedAt()).isNotNull();
        mvc.perform(get("/").with(as(OTHER_STUDENT)))
                .andExpect(content().string(containsString("Navy umbrella")))
                .andExpect(content().string(containsString("stage-returned")));
        mvc.perform(get("/?hideClaimed=on").with(as(OTHER_STUDENT)))
                .andExpect(content().string(not(containsString("Navy umbrella"))));
    }

    @Test
    void removingTakesTheReportOffTheFeedAndDeletesThePhoto() throws Exception {
        Item approved = item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, STUDENT);
        approved.setImagePath("items/photo.jpg");
        Item item = save(approved);

        mvc.perform(post(action(item, "remove")).param("reason", "Spam").param("returnTo", "/")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/"));

        assertThat(reload(item).getStatus()).isEqualTo(ReportStatus.REMOVED);
        verify(storage).deletePhotoQuietly("items/photo.jpg");
        mvc.perform(get("/").with(as(OTHER_STUDENT))).andExpect(content().string(not(containsString("Navy umbrella"))));
    }

    @Test
    void actionsThatDontFitTheStatusAreRefusedWithAMessage() throws Exception {
        Item pending = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));
        Item rejected = save(item("Old keys", ReportType.LOST, ReportStatus.REJECTED, STUDENT));

        mvc.perform(claim(pending).with(as(ADMIN)).with(csrf()))
                .andExpect(status().isOk())   // the claim form is shown again with the message
                .andExpect(content().string(containsString("can&#39;t be marked as claimed")));
        mvc.perform(post(action(rejected, "approve")).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Rejected")));

        assertThat(reload(pending).getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(reload(rejected).getStatus()).isEqualTo(ReportStatus.REJECTED);
    }

    @Test
    void onlyAdminsCanModerate() throws Exception {
        Item item = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));

        mvc.perform(post(action(item, "approve")).with(as(STUDENT)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(action(item, "approve")).with(as(STAFF)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(action(item, "approve")).with(as(ADMIN))).andExpect(status().isForbidden()); // no CSRF
        for (String page : new String[]{"/admin", "/admin/pending", "/admin/reports", "/admin/activity"}) {
            mvc.perform(get(page).with(as(STUDENT))).andExpect(status().isForbidden());
            mvc.perform(get(page).with(as(STAFF))).andExpect(status().isForbidden());
        }
        assertThat(reload(item).getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void unknownReportIs404AndExternalReturnUrlsAreIgnored() throws Exception {
        mvc.perform(post("/admin/reports/" + UUID.randomUUID() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(status().isNotFound());

        Item item = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));
        mvc.perform(post(action(item, "approve")).param("returnTo", "https://evil.example")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/pending"));
    }

    @Test
    void pendingQueueShowsTheOldestSubmissionFirst() throws Exception {
        save(item("Newest report", ReportType.LOST, ReportStatus.PENDING, STUDENT, minutesAgo(1)));
        save(item("Oldest report", ReportType.LOST, ReportStatus.PENDING, STUDENT, minutesAgo(60)));
        save(item("Middle report", ReportType.LOST, ReportStatus.PENDING, STUDENT, minutesAgo(30)));

        String html = mvc.perform(get("/admin/pending").with(as(ADMIN))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("3 reports are waiting").contains(STUDENT.email());
        int next = html.indexOf("Next up");
        assertThat(next).isPositive().isLessThan(html.indexOf("Oldest report"));
        assertThat(html.indexOf("Oldest report")).isLessThan(html.indexOf("Middle report"));
        assertThat(html.indexOf("Middle report")).isLessThan(html.indexOf("Newest report"));
    }

    @Test
    void overviewAllReportsAndActivityLogWork() throws Exception {
        save(item("Pending one", ReportType.LOST, ReportStatus.PENDING, STUDENT));
        save(item("Lost one", ReportType.LOST, ReportStatus.APPROVED, STUDENT));
        Item found = save(item("Found one", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        mvc.perform(claim(found).with(as(ADMIN)).with(csrf()));

        mvc.perform(get("/admin").with(as(ADMIN))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Pending review")))
                .andExpect(content().string(containsString("sbadge-done")))     // the "Claimed" status badge
                .andExpect(content().string(containsString("Claimed</span>")));
        mvc.perform(get("/admin/reports?status=claimed").with(as(ADMIN)))
                .andExpect(content().string(containsString("Found one")))
                .andExpect(content().string(not(containsString("Lost one"))));
        mvc.perform(get("/admin/reports?q=maria_santos").with(as(ADMIN)))   // search by reporter email
                .andExpect(content().string(containsString("Found one")))
                .andExpect(content().string(not(containsString("Pending one"))));
        mvc.perform(get("/admin/activity?action=claimed").with(as(ADMIN)))
                .andExpect(content().string(containsString("Found one")))
                .andExpect(content().string(containsString(ADMIN.name())));
    }

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder claim(Item item) {
        return post(action(item, "claim"))
                .param("claimantName", "Pedro Reyes")
                .param("claimantAffiliation", "BS Nursing · 1st year · N1A")
                .param("releasedAt", java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Manila"))
                        .minusMinutes(5).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString())
                .param("notes", "Verified with school ID");
    }

    static String action(Item item, String verb) {
        return "/admin/reports/" + item.getId() + "/" + verb;
    }

    private Item reload(Item item) {
        return items.findById(item.getId()).orElseThrow();
    }

    private ActivityLogEntry lastLog() {
        return activityLog.findTop10ByOrderByCreatedAtDescIdDesc().get(0);
    }

    @Test
    void adminSearchTreatsPercentAndUnderscoreAsPlainText() throws Exception {
        save(item("100% cotton shirt", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        save(item("Blue umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        assertThat(adminSearch("")).contains("100% cotton shirt").contains("Blue umbrella");
        assertThat(adminSearch("100%")).contains("100% cotton shirt").doesNotContain("Blue umbrella");
        // In SQL LIKE, _ matches any one character; here it's only an underscore, so "b_ue" finds nothing.
        assertThat(adminSearch("b_ue")).doesNotContain("Blue umbrella").contains("No reports match");
    }

    private String adminSearch(String q) throws Exception {
        return mvc.perform(get("/admin/reports").param("q", q).with(as(ADMIN))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
