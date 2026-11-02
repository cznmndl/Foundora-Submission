package com.foundora.feed;

import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.storage.StorageService;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostAccessTest extends WebTestBase {

    @Test
    void pendingReportIsVisibleOnlyToItsReporterAndAdmins() throws Exception {
        Item pending = save(item("Navy umbrella", ReportType.LOST, ReportStatus.PENDING, STUDENT));
        String url = "/posts/" + pending.getId();

        mvc.perform(get(url).with(as(STUDENT))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Status: Pending")));
        mvc.perform(get(url).with(as(ADMIN))).andExpect(status().isOk())
                .andExpect(content().string(containsString(STUDENT.email())));
        mvc.perform(get(url).with(as(OTHER_STUDENT))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(STAFF))).andExpect(status().isNotFound());
    }

    @Test
    void rejectedReportShowsTheReasonToItsReporter() throws Exception {
        Item rejected = save(item("Navy umbrella", ReportType.LOST, ReportStatus.REJECTED, STUDENT));

        mvc.perform(get("/posts/" + rejected.getId()).with(as(STUDENT)))
                .andExpect(content().string(containsString("Reason: Duplicate report")));
        mvc.perform(get("/my-reports").with(as(STUDENT)))
                .andExpect(content().string(containsString("Duplicate report")))
                .andExpect(content().string(containsString("Rejected")));
    }

    @Test
    void removedReportIsVisibleOnlyToAdmins() throws Exception {
        Item removed = save(item("Navy umbrella", ReportType.LOST, ReportStatus.REMOVED, STUDENT));

        mvc.perform(get("/posts/" + removed.getId()).with(as(STUDENT))).andExpect(status().isNotFound());
        mvc.perform(get("/posts/" + removed.getId()).with(as(ADMIN))).andExpect(status().isOk());
    }

    @Test
    void approvedReportIsVisibleToEveryoneButShowsNoEmailToNonAdmins() throws Exception {
        Item approved = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        mvc.perform(get("/posts/" + approved.getId()).with(as(STAFF))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Claim this item at the SDFO office.")))
                .andExpect(content().string(not(containsString(OTHER_STUDENT.email()))));
    }

    @Test
    void unknownReportIs404() throws Exception {
        mvc.perform(get("/posts/" + java.util.UUID.randomUUID()).with(as(STUDENT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void errorPagesAreFriendly() throws Exception {
        String[][] pages = {{"404", "find that"}, {"403", "for admins only"}, {"500", "went wrong on our side"}};
        for (String[] page : pages) {
            mvc.perform(get("/error").with(as(STUDENT)).accept(MediaType.TEXT_HTML)
                            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, Integer.parseInt(page[0]))
                            .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/x"))
                    .andExpect(status().is(Integer.parseInt(page[0])))
                    .andExpect(content().string(containsString(page[1])))
                    .andExpect(content().string(containsString("Back to the feed")));
        }
    }

    @Test
    void photosFollowTheSameAccessRules() throws Exception {
        Item approved = item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        approved.setImagePath("items/a.jpg");
        approved = save(approved);
        Item pending = item("Black wallet", ReportType.LOST, ReportStatus.PENDING, OTHER_STUDENT);
        pending.setImagePath("items/p.jpg");
        pending = save(pending);
        Item noPhoto = save(item("Keys", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT));
        when(storage.downloadPhoto("items/a.jpg"))
                .thenReturn(new StorageService.StoredPhoto(new byte[]{1, 2}, MediaType.IMAGE_JPEG));
        when(storage.downloadPhoto("items/p.jpg"))
                .thenReturn(new StorageService.StoredPhoto(new byte[]{3}, MediaType.IMAGE_JPEG));

        mvc.perform(get("/photos/" + approved.getId()).with(as(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(new byte[]{1, 2}))
                .andExpect(header().string("Cache-Control", containsString("private")));
        mvc.perform(get("/photos/" + pending.getId()).with(as(STUDENT))).andExpect(status().isNotFound());
        mvc.perform(get("/photos/" + pending.getId()).with(as(OTHER_STUDENT))).andExpect(status().isOk());
        mvc.perform(get("/photos/" + pending.getId()).with(as(ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/photos/" + noPhoto.getId()).with(as(STUDENT))).andExpect(status().isNotFound());
    }

    @Test
    void signedOutVisitorsAreSentToLogin() throws Exception {
        mvc.perform(get("/photos/" + java.util.UUID.randomUUID()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/my-reports")).andExpect(status().is3xxRedirection());
    }
}
