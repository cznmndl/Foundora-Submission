package com.foundora.moderation;

import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

class ConcurrentModerationTest extends WebTestBase {

    @MockitoSpyBean
    private ModerationService moderation;

    @Test
    void theSecondAdminIsToldTheReportWasAlreadyHandled() throws Exception {
        Item report = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));

        mvc.perform(post("/admin/reports/" + report.getId() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("Approved")));
        mvc.perform(post("/admin/reports/" + report.getId() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", "\"Navy umbrella\" is Approved, so it can't be approved."));

        mvc.perform(post("/admin/reports/" + report.getId() + "/remove").with(as(ADMIN)).with(csrf()));
        mvc.perform(post("/admin/reports/" + report.getId() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", "\"Navy umbrella\" is Removed, so it can't be approved."));
        assertThat(items.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.REMOVED);
    }

    @Test
    void aChangeThatClashesWithAnotherAdminsIsRefusedWithAMessage() throws Exception {
        Item report = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));
        doThrow(new ObjectOptimisticLockingFailureException(Item.class, report.getId()))
                .when(moderation).approve(eq(report.getId()), any());

        mvc.perform(post("/admin/reports/" + report.getId() + "/approve").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError",
                        "Someone else changed this report at the same moment. Check it and try again."));

        assertThat(items.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void anOutOfDateCopyOfAReportCannotOverwriteANewerChange() {
        Item saved = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.PENDING, STUDENT));
        Item first = items.findById(saved.getId()).orElseThrow();
        Item second = items.findById(saved.getId()).orElseThrow();

        first.setStatus(ReportStatus.APPROVED);
        items.save(first);
        second.setStatus(ReportStatus.REJECTED);
        second.setReviewReason("Duplicate");

        assertThatThrownBy(() -> items.save(second)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(items.findById(saved.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.APPROVED);
    }
}
