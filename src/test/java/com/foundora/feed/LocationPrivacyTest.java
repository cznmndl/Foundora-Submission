package com.foundora.feed;

import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Locations are secret: when someone claims an item, SDFO asks where they lost it. These tests check the HTML
 * actually sent to students and staff, not just what's visible.
 */
class LocationPrivacyTest extends WebTestBase {

    // A location that isn't in the dropdown list (like reports filed before the list existed), so it can't
    // appear on a page just because the report form's dropdown is there.
    private static final String PLACE = "Old Annex Building";
    private static final String SPOT = "Taped under the court 2 scoreboard";

    @Test
    void studentsAndStaffNeverReceiveTheLocationOrSpot() throws Exception {
        Item post = save(secretItem("Navy umbrella", ReportStatus.APPROVED));

        for (RequestPostProcessor user : new RequestPostProcessor[]{as(STUDENT), as(STAFF)}) {
            for (String url : new String[]{"/", "/feed/posts", "/posts/" + post.getId(), "/?show=claimed",
                    "/?location=" + PLACE}) {
                assertThat(html(url, user)).as(url).doesNotContain(PLACE).doesNotContain(SPOT)
                        .doesNotContain("scoreboard").doesNotContain("Admin only");
            }
        }
    }

    @Test
    void searchingForTheLocationFindsNothing() throws Exception {
        save(secretItem("Navy umbrella", ReportStatus.APPROVED));

        assertThat(html("/?q=annex", as(STUDENT))).doesNotContain("Navy umbrella");
        assertThat(html("/?q=scoreboard", as(STUDENT))).doesNotContain("Navy umbrella");
        assertThat(html("/?q=umbrella", as(STUDENT))).contains("Navy umbrella");
    }

    @Test
    void theLocationFilterIsIgnoredForNonAdmins() throws Exception {
        Item atGazebo = item("Gazebo tumbler", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        atGazebo.setLocation("Gazebo");
        save(atGazebo);
        Item atOval = item("Oval umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        atOval.setLocation("Oval");
        save(atOval);

        // A student guessing ?location=Gazebo sees every post: the filter would otherwise reveal the place.
        assertThat(html("/?location=Gazebo", as(STUDENT))).contains("Gazebo tumbler").contains("Oval umbrella")
                .doesNotContain("id=\"location-filter\"");
        assertThat(html("/feed/posts?location=Gazebo", as(STUDENT))).contains("Oval umbrella");

        // Admins can filter by location.
        assertThat(html("/?location=Gazebo", as(ADMIN))).contains("Gazebo tumbler").doesNotContain("Oval umbrella")
                .contains("id=\"location-filter\"");
    }

    @Test
    void adminsSeeLocationSpotAndReporterDetails() throws Exception {
        Item post = save(secretItem("Navy umbrella", ReportStatus.APPROVED));

        for (String url : new String[]{"/", "/posts/" + post.getId()}) {
            assertThat(html(url, as(ADMIN))).as(url).contains(PLACE).contains(SPOT).contains("Admin only")
                    .contains(OTHER_STUDENT.email()).contains("BS Psychology · 3rd year · P3B");
        }
        Item pending = save(secretItem("Black wallet", ReportStatus.PENDING));
        assertThat(html("/admin/pending", as(ADMIN))).contains("Black wallet").contains(PLACE).contains(SPOT);
        assertThat(html("/admin/reports?q=scoreboard", as(ADMIN))).contains("Navy umbrella").contains("Black wallet");
        assertThat(pending.getId()).isNotNull();
    }

    @Test
    void reportersSeeTheirOwnLocationsOnMyReports() throws Exception {
        save(secretItem("Navy umbrella", ReportStatus.APPROVED));

        assertThat(html("/my-reports", as(OTHER_STUDENT))).contains(PLACE).contains(SPOT);
        assertThat(html("/my-reports", as(STUDENT))).doesNotContain(PLACE);
    }

    private static Item secretItem(String name, ReportStatus status) {
        Item item = item(name, ReportType.FOUND, status, OTHER_STUDENT);
        item.setLocation(PLACE);
        item.setLocationSpot(SPOT);
        return item;
    }

    private String html(String url, RequestPostProcessor user) throws Exception {
        return mvc.perform(get(url).with(user)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
