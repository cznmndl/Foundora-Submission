package com.foundora.feed;

import com.foundora.database.entity.Category;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static com.foundora.support.TestData.minutesAgo;
import static com.foundora.support.TestData.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeedPageTest extends WebTestBase {

    @Test
    void feedShowsOnlyApprovedAndClaimedReports() throws Exception {
        save(item("Approved umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        save(item("Claimed tumbler", ReportType.FOUND, ReportStatus.CLAIMED, OTHER_STUDENT));
        save(item("Pending wallet", ReportType.LOST, ReportStatus.PENDING, OTHER_STUDENT));
        save(item("Rejected keys", ReportType.LOST, ReportStatus.REJECTED, OTHER_STUDENT));
        save(item("Removed calculator", ReportType.LOST, ReportStatus.REMOVED, OTHER_STUDENT));

        mvc.perform(get("/").with(as(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Approved umbrella")))
                .andExpect(content().string(containsString("Claimed tumbler")))
                .andExpect(content().string(containsString("Returned to owner")))
                .andExpect(content().string(not(containsString("Pending wallet"))))
                .andExpect(content().string(not(containsString("Rejected keys"))))
                .andExpect(content().string(not(containsString("Removed calculator"))));
    }

    @Test
    void reporterEmailNeverAppearsOnPublicPages() throws Exception {
        Item post = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        for (String url : new String[]{"/", "/feed/posts", "/posts/" + post.getId(), "/?q=navy"}) {
            String html = mvc.perform(get(url).with(as(STUDENT)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(html).as(url).contains("Navy umbrella").contains("Maria Santos")
                    .doesNotContain(OTHER_STUDENT.email());
        }
    }

    @Test
    void foundAndLostPostsCarryTheirSdfoNotes() throws Exception {
        save(item("Found ID", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        save(item("Lost bag", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT));

        mvc.perform(get("/").with(as(STUDENT)))
                .andExpect(content().string(containsString("Held at the SDFO office")))
                .andExpect(content().string(containsString("Found it? Bring it to SDFO")));
    }

    @Test
    void filtersByTypeCategoryTextAndClaimed() throws Exception {
        Item umbrella = item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        save(umbrella);
        Item phone = item("Black phone", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT);
        phone.setCategory(Category.ELECTRONICS);
        phone.setDescription("Samsung with a cracked case");
        save(phone);
        Item claimed = item("Green umbrella", ReportType.FOUND, ReportStatus.CLAIMED, OTHER_STUDENT);
        save(claimed);

        assertFeed("/?type=lost", "Black phone", "Navy umbrella");
        assertFeed("/?category=electronics", "Black phone", "Navy umbrella");
        assertFeed("/?q=umb", "Navy umbrella", "Black phone");          // prefix of a word
        assertFeed("/?q=cracked+samsung", "Black phone", "Navy umbrella"); // every word must match
        assertFeed("/?q=umbrella&hideClaimed=on", "Navy umbrella", "Green umbrella");
    }

    @Test
    void filtersByDateRangeAndSortsOldestFirst() throws Exception {
        Item older = item("Older report", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT, minutesAgo(120));
        older.setDateLostFound(today().minusDays(20));
        save(older);
        Item newer = item("Newer report", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT, minutesAgo(5));
        save(newer);

        assertFeed("/?from=" + today().minusDays(5), "Newer report", "Older report");
        assertFeed("/?to=" + today().minusDays(10), "Older report", "Newer report");

        String newestFirst = html("/");
        assertThat(newestFirst.indexOf("Newer report")).isLessThan(newestFirst.indexOf("Older report"));
        String oldestFirst = html("/?sort=oldest");
        assertThat(oldestFirst.indexOf("Older report")).isLessThan(oldestFirst.indexOf("Newer report"));
    }

    @Test
    void loadsTenPostsAtATime() throws Exception {
        for (int i = 1; i <= 12; i++) {
            items.save(item("Item number " + i + "x", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT,
                    minutesAgo(100 - i)));
        }
        feedIndex.rebuild();

        String first = html("/");
        assertThat(first).contains("12 items").contains("Item number 12x").contains("Item number 3x")
                .doesNotContain("Item number 2x").contains("data-load-more").contains("/feed/posts?page=1");

        String more = html("/feed/posts?page=1");
        assertThat(more).contains("Item number 2x").contains("Item number 1x").doesNotContain("Item number 3x")
                .doesNotContain("data-load-more").doesNotContain("<html");
    }

    @Test
    void emptyStatesExplainWhatToDo() throws Exception {
        assertThat(html("/")).contains("Nothing on the board yet").contains("Report an item");
        assertThat(html("/?type=found")).contains("No found items yet").contains("Clear filters");
    }

    @Test
    void onlyAdminsGetModerationControlsOnPosts() throws Exception {
        Item post = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        String action = "/admin/reports/" + post.getId() + "/claim";

        assertThat(html("/", as(STUDENT))).doesNotContain(action).doesNotContain("Mark as claimed");
        assertThat(html("/", as(STAFF))).doesNotContain(action);
        assertThat(html("/", as(ADMIN))).contains(action).contains("Mark as claimed").contains("Remove");
    }

    @Test
    void headerShowsNameAndRoleAndAdminNavOnlyForAdmins() throws Exception {
        assertThat(html("/", as(STUDENT))).contains("Juan Dela Cruz").contains("Student")
                .doesNotContain("href=\"/admin\"");
        assertThat(html("/", as(STAFF))).contains("Ben Reyes").contains("Staff").doesNotContain("href=\"/admin\"");
        assertThat(html("/", as(ADMIN))).contains("Ana Cruz").contains("Admin").contains("href=\"/admin\"");
    }

    @Test
    void searchSuggestionsNeverRevealLocations() throws Exception {
        Item post = item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        post.setLocation("Gazebo");
        post.setLocationSpot("Bleachers behind the stage");
        save(post);

        mvc.perform(get("/api/suggest/search?q=umb").with(as(STUDENT)))
                .andExpect(content().json("[\"umbrella\"]"));
        mvc.perform(get("/api/suggest/search?q=gaz").with(as(STUDENT))).andExpect(content().json("[]"));
        mvc.perform(get("/api/suggest/search?q=blea").with(as(STUDENT))).andExpect(content().json("[]"));
        mvc.perform(get("/api/suggest/locations?q=gaz").with(as(STUDENT))).andExpect(status().isNotFound());
    }

    private void assertFeed(String url, String shown, String hidden) throws Exception {
        assertThat(html(url)).as(url).contains(shown).doesNotContain(hidden);
    }

    private String html(String url) throws Exception {
        return html(url, as(STUDENT));
    }

    @Test
    void searchCopesWithEmptyTextSpecialCharactersAndNoResults() throws Exception {
        save(item("100% cotton shirt", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        // An empty search shows everything.
        assertThat(search("")).contains("100% cotton shirt");
        // Odd characters are just text: no error, and nothing typed is echoed back as markup.
        for (String q : new String[]{"%", "_", "'", "\"", "\\", "(((", "<script>alert(1)</script>"}) {
            assertThat(search(q)).as(q).doesNotContain("<script>alert(1)</script>");
        }
        assertThat(search("100%")).contains("100% cotton shirt");
        // No results: the empty board says so and offers to clear the filters.
        assertThat(search("zzzzzz")).doesNotContain("100% cotton shirt").contains("Clear filters");
    }

    private String search(String q) throws Exception {
        return mvc.perform(get("/").param("q", q).with(as(STUDENT))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
