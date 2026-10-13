package com.foundora.controller;

import com.foundora.model.ActivityAction;
import com.foundora.model.ActivityLogEntry;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ActivityLogFilterTest extends WebTestBase {

    @BeforeEach
    void someActivity() {
        Item tumbler = save(item("Blue tumbler", ReportType.FOUND, ReportStatus.APPROVED, STUDENT));
        Item umbrella = save(item("Red umbrella", ReportType.LOST, ReportStatus.APPROVED, STUDENT));
        Item keys = save(item("House keys", ReportType.FOUND, ReportStatus.APPROVED, STUDENT));
        Instant now = Instant.now();
        activityLog.save(new ActivityLogEntry(tumbler, ActivityAction.SUBMITTED, STUDENT.email(), STUDENT.name(), null, now));
        activityLog.save(new ActivityLogEntry(umbrella, ActivityAction.CLAIM_REOPENED, ADMIN.email(), ADMIN.name(), null, now));
        activityLog.save(new ActivityLogEntry(keys, ActivityAction.CLAIM_VERIFIED, ADMIN.email(), ADMIN.name(), null, now));
    }

    @Test
    void everyActionBelongsToOneCategory() {
        for (ActivityAction action : ActivityAction.values()) {
            assertThat(action.group().actions()).contains(action);
        }
    }

    @Test
    void categoryTabsShowOnlyTheirActions() throws Exception {
        assertThat(html("/admin/activity")).contains("Blue tumbler", "Red umbrella", "House keys");
        assertThat(html("/admin/activity?group=reports")).contains("Blue tumbler")
                .doesNotContain("Red umbrella").doesNotContain("House keys");
        assertThat(html("/admin/activity?group=claims")).contains("Red umbrella").doesNotContain("Blue tumbler");
        assertThat(html("/admin/activity?group=claim-requests")).contains("House keys").doesNotContain("Red umbrella");
    }

    @Test
    void theActionDropdownListsOnlyTheChosenCategory() throws Exception {
        String claims = html("/admin/activity?group=claims");
        assertThat(claims).contains("Marked claim as disputed").contains("value=\"claim-reopened\"")
                .doesNotContain("value=\"submitted\"").doesNotContain("<optgroup");
        assertThat(html("/admin/activity")).contains("<optgroup label=\"Claim requests\"")
                .contains("Asked for more information");
    }

    @Test
    void anActionOutsideTheCategoryIsIgnoredAndAnActionAloneSelectsItsCategory() throws Exception {
        assertThat(html("/admin/activity?group=reports&action=claim-verified")).contains("Blue tumbler")
                .doesNotContain("House keys");
        assertThat(html("/admin/activity?action=claim-verified")).contains("House keys")
                .doesNotContain("Blue tumbler").contains("name=\"group\" value=\"claim-requests\"");
    }

    private String html(String url) throws Exception {
        return mvc.perform(get(url).with(as(ADMIN))).andReturn().getResponse().getContentAsString();
    }
}
