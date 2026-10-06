package com.foundora.controller;

import com.foundora.model.ActivityAction;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
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
                .andExpect(content().string(containsString("Your report was sent!")))
                .andExpect(content().string(containsString("Please bring the item to the SDFO office.")));
        mvc.perform(get("/report/sent").with(as(STUDENT)))
                .andExpect(redirectedUrl("/my-reports"));
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

    static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }
}
