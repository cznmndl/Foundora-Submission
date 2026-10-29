package com.foundora.reporting;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.reporting.ImageProcessor;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.png;
import static com.foundora.support.TestData.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportSubmissionTest extends WebTestBase {

    @Test
    void savesPendingReportWithReporterFromTheSessionNotTheForm() throws Exception {
        mvc.perform(validReport("FOUND")
                        .param("reporterName", "Someone Else")
                        .param("reporterEmail", "evil@example.com")
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent"))
                .andExpect(flash().attribute("sentType", ReportType.FOUND));

        List<Item> saved = items.findAll();
        assertThat(saved).hasSize(1);
        Item item = saved.get(0);
        assertThat(item.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(item.getReporterEmail()).isEqualTo(STUDENT.email());
        assertThat(item.getReporterName()).isEqualTo(STUDENT.name());
        assertThat(item.getReporterUid()).isEqualTo(STUDENT.uid());
        assertThat(item.getImagePath()).isNull();
        assertThat(item.getLocation()).isEqualTo("Sentrum");
        assertThat(item.getLocationSpot()).isEqualTo("2nd floor near the elevator");
        assertThat(activityLog.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getAction()).isEqualTo(ActivityAction.SUBMITTED));
    }

    @Test
    void staffCanReportLostItems() throws Exception {
        mvc.perform(validReport("LOST").with(as(STAFF)).with(csrf())).andExpect(redirectedUrl("/report/sent"));

        assertThat(items.findAll()).singleElement()
                .satisfies(i -> assertThat(i.getReportType()).isEqualTo(ReportType.LOST));
    }

    @Test
    void rejectsFutureDatesUsingPhilippineTime() throws Exception {
        mvc.perform(report("LOST", today().plusDays(1).toString()).with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The date can’t be in the future.")));

        assertThat(items.count()).isZero();
    }

    @Test
    void showsAClearMessageForEachMissingField() throws Exception {
        mvc.perform(multipart("/report").with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose whether you lost or found the item.")))
                .andExpect(content().string(containsString("Enter the item’s name.")))
                .andExpect(content().string(containsString("Choose a category.")))
                .andExpect(content().string(containsString("Choose where the item was lost or found.")))
                .andExpect(content().string(containsString("Describe the specific spot")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")));

        assertThat(items.count()).isZero();
    }

    @Test
    void locationMustComeFromTheListAndOtherNeedsTheSpot() throws Exception {
        mvc.perform(report("LOST", "Somewhere I typed", "Hallway").with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose a place from the list")));
        mvc.perform(report("LOST", "Other", " ").with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("so describe where it was")));
        assertThat(items.count()).isZero();

        mvc.perform(report("LOST", "Other", "Parking lot near Gate 3").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent"));
        assertThat(items.findAll()).singleElement().satisfies(i -> {
            assertThat(i.getLocation()).isEqualTo("Other");
            assertThat(i.getLocationSpot()).isEqualTo("Parking lot near Gate 3");
        });
    }

    @Test
    void formOffersTheCampusLocationsWithOtherLast() throws Exception {
        String html = mvc.perform(get("/report").with(as(STUDENT))).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Mabini Building (MB)").contains("Chez Rafael Canteen").contains("JRF")
                .contains("Specific spot");
        assertThat(html.indexOf(">JRF<")).isLessThan(html.indexOf(">Other<"));
    }

    @Test
    void reEncodesThePhotoBeforeStoringIt() throws Exception {
        when(storage.storePhoto(any())).thenReturn("items/stored.jpg");

        mvc.perform(validReport("FOUND")
                        .file(new MockMultipartFile("photo", "umbrella.png", "image/png", png(400, 300)))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent"));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storage).storePhoto(stored.capture());
        assertThat(stored.getValue()).startsWith((byte) 0xFF, (byte) 0xD8); // JPEG, whatever was uploaded
        assertThat(items.findAll()).singleElement()
                .satisfies(i -> assertThat(i.getImagePath()).isEqualTo("items/stored.jpg"));
    }

    @Test
    void refusesFilesThatAreNotPhotos() throws Exception {
        mvc.perform(validReport("FOUND")
                        .file(new MockMultipartFile("photo", "x.png", "image/png", "<html>".getBytes()))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("isn&#39;t a photo we can read")));

        verify(storage, never()).storePhoto(any());
        assertThat(items.count()).isZero();
    }

    @Test
    void sentPageRemindsFinderToBringTheItemToSdfo() throws Exception {
        mvc.perform(get("/report/sent").with(as(STUDENT)).flashAttr("sentType", ReportType.FOUND))
                .andExpect(content().string(containsString("Sent to SDFO")))
                .andExpect(content().string(containsString("Bring the item to the SDFO office")));
        mvc.perform(get("/report/sent").with(as(STUDENT)))
                .andExpect(redirectedUrl("/my-reports"));
    }

    @Test
    void theSentPageShowsTheReportersCardUnderReview() throws Exception {
        var result = mvc.perform(validReport("FOUND").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent")).andReturn();
        mvc.perform(get("/report/sent").with(as(STUDENT)).flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("Navy umbrella")))
                .andExpect(content().string(containsString("Under review")));
    }

    @Test
    void privateDetailsAreSavedAndShownOnlyToTheReporterAndAdmins() throws Exception {
        mvc.perform(validReport("FOUND").param("privateDetails", "  Initials JDC carved in the handle ")
                .with(as(STUDENT)).with(csrf())).andExpect(redirectedUrl("/report/sent"));
        Item saved = items.findAll().get(0);
        assertThat(saved.getPrivateDetails()).isEqualTo("Initials JDC carved in the handle");

        saved.setStatus(ReportStatus.APPROVED);
        items.save(saved);
        feedIndex.rebuild();
        for (String url : new String[]{"/", "/posts/" + saved.getId()}) {
            assertThat(mvc.perform(get(url).with(as(STAFF))).andReturn().getResponse().getContentAsString())
                    .as(url).doesNotContain("carved in the handle").contains("Navy umbrella");
        }
        // Not searchable either: searching for its words finds nothing.
        assertThat(mvc.perform(get("/?q=carved").with(as(STAFF))).andReturn().getResponse().getContentAsString())
                .doesNotContain("Navy umbrella");
        assertThat(mvc.perform(get("/my-reports").with(as(STUDENT))).andReturn().getResponse()
                .getContentAsString()).contains("Initials JDC carved in the handle");
        assertThat(mvc.perform(get("/posts/" + saved.getId()).with(as(com.foundora.support.TestData.ADMIN)))
                .andReturn().getResponse().getContentAsString()).contains("Initials JDC carved in the handle");
    }

    @Test
    void privateDetailsAreOptionalAndBlankMeansNone() throws Exception {
        mvc.perform(validReport("LOST").param("privateDetails", "   ").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent"));
        assertThat(items.findAll().get(0).getPrivateDetails()).isNull();
        mvc.perform(validReport("LOST").param("privateDetails", "x".repeat(501)).with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep the private details to 500 characters")));
    }

    @Test
    void submittingRequiresCsrfToken() throws Exception {
        mvc.perform(validReport("LOST").with(as(STUDENT))).andExpect(status().isForbidden());
    }

    private static MockMultipartHttpServletRequestBuilder validReport(String type) {
        return report(type, today().toString());
    }

    private static MockMultipartHttpServletRequestBuilder report(String type, String date) {
        return report(type, date, "Sentrum", "2nd floor near the elevator");
    }

    private static MockMultipartHttpServletRequestBuilder report(String type, String location, String spot) {
        return report(type, today().toString(), location, spot);
    }

    private static MockMultipartHttpServletRequestBuilder report(String type, String date, String location,
                                                                 String spot) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/report")
                .param("type", type)
                .param("itemName", "Navy umbrella")
                .param("description", "Folding umbrella with a wooden handle")
                .param("category", "UMBRELLAS")
                .param("location", location)
                .param("locationSpot", spot)
                .param("dateLostFound", date);
    }

    @Test
    void refusesFieldsThatAreTooLong() throws Exception {
        mvc.perform(multipart("/report").param("type", "LOST").param("itemName", "x".repeat(81))
                        .param("description", "x".repeat(1001)).param("category", "UMBRELLAS")
                        .param("location", "Sentrum").param("locationSpot", "x".repeat(201))
                        .param("dateLostFound", today().toString()).with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Keep the item name to 80 characters or fewer.")))
                .andExpect(content().string(containsString("Keep the description to 1000 characters or fewer.")))
                .andExpect(content().string(containsString("Keep the specific spot to 200 characters or fewer.")));

        assertThat(items.count()).isZero();
    }

    @Test
    void refusesAFileRenamedToLookLikeAPhoto() throws Exception {
        mvc.perform(validReport("FOUND")
                        .file(new MockMultipartFile("photo", "receipt.jpg", "image/jpeg", "%PDF-1.7 not a photo".getBytes()))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("isn&#39;t a photo we can read")));

        verify(storage, never()).storePhoto(any());
        assertThat(items.count()).isZero();
    }

    @Test
    void refusesPhotosOverFiveMegabytes() throws Exception {
        byte[] tooBig = new byte[(int) ImageProcessor.MAX_BYTES + 1];

        mvc.perform(validReport("FOUND").file(new MockMultipartFile("photo", "big.jpg", "image/jpeg", tooBig))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The photo must be 5 MB or smaller.")));

        verify(storage, never()).storePhoto(any());
        assertThat(items.count()).isZero();
    }

    @Test
    void keepsTheFormWhenThePhotoCannotBeUploaded() throws Exception {
        when(storage.storePhoto(any())).thenThrow(new ResourceAccessException("Supabase Storage unreachable"));

        mvc.perform(validReport("FOUND")
                        .file(new MockMultipartFile("photo", "umbrella.png", "image/png", png(400, 300)))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("We couldn&#39;t save the photo just now")))
                .andExpect(content().string(containsString("value=\"Navy umbrella\"")));

        assertThat(items.count()).isZero();
        assertThat(activityLog.count()).isZero();
    }
}
