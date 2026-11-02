package com.foundora.claims;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.ClaimRequestStatus;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.ItemStage;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.database.storage.StorageService;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static com.foundora.support.TestData.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClaimRequestTest extends WebTestBase {

    private static final String PROOF = "Lost it at the Sentrum benches on Monday. It has a cat sticker on the lid.";

    @Autowired
    private ClaimRequestService claimRequests;

    @Test
    void requestingAFoundItemMakesItClaimPending() throws Exception {
        Item item = found("Blue tumbler");

        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/posts/" + item.getId() + "#claim"))
                .andExpect(flash().attribute("flashSuccess", containsString("Request sent")));

        ClaimRequest r = only();
        assertThat(r.getStatus()).isEqualTo(ClaimRequestStatus.PENDING);
        assertThat(r.getRequesterName()).isEqualTo(STUDENT.name());
        assertThat(r.getRequesterAffiliation()).isEqualTo("BS Information Technology · 2nd year · O1A");
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_REQUESTED);
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.CLAIM_PENDING);
        assertThat(html("/", as(OTHER_STUDENT))).contains("Claim pending");
    }

    @Test
    void onlyFoundItemsOthersReportedCanBeRequestedOnce() throws Exception {
        Item lost = save(item("Black umbrella", ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT));
        mvc.perform(request(lost, PROOF).with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("lost-item report")));

        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(OTHER_STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("You reported this item")));
        mvc.perform(request(item, "mine").with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("how you know it's yours")));

        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("already asked")));
        assertThat(claimRequestRepository.findAll()).hasSize(1);
    }

    @Test
    void theProofIsSeenOnlyByTheRequesterAndAdmins() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));

        assertThat(html("/posts/" + item.getId(), as(STUDENT))).contains("cat sticker").contains("Withdraw request");
        assertThat(html("/posts/" + item.getId(), as(STAFF))).doesNotContain("cat sticker")
                .doesNotContain(STUDENT.name()).contains("This is mine");
        assertThat(html("/admin/claims", as(ADMIN))).contains("cat sticker").contains(STUDENT.email())
                .contains("Bench beside the vending machine");
        assertThat(html("/my-reports", as(STUDENT))).contains("My claim requests").contains("Blue tumbler");
    }

    @Test
    void studentsCannotReviewRequests() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        UUID id = only().getId();

        mvc.perform(get("/admin/claims").with(as(STAFF))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/claim-requests/" + id + "/verify").with(as(OTHER_STUDENT)).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.PENDING);
    }

    @Test
    void verifyingGivesASevenDayPassAndOnlyOnePerItem() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        mvc.perform(request(item, "It's mine, I left it after PE class on Monday.").with(as(STAFF)).with(csrf()));
        List<ClaimRequest> both = claimRequestRepository.findByItemIdOrderByCreatedAtAsc(item.getId());

        mvc.perform(admin("verify", both.get(0).getId()))
                .andExpect(flash().attribute("flashSuccess", containsString(STUDENT.name())));
        ClaimRequest verified = claimRequestRepository.findById(both.get(0).getId()).orElseThrow();
        assertThat(verified.getStatus()).isEqualTo(ClaimRequestStatus.VERIFIED);
        assertThat(verified.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.VERIFIED);
        assertThat(html("/posts/" + item.getId(), as(STUDENT))).contains("Bring your school ID");

        mvc.perform(admin("verify", both.get(1).getId()))
                .andExpect(flash().attribute("flashError", containsString("already verified")));
        assertThat(claimRequestRepository.findById(both.get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ClaimRequestStatus.PENDING);
    }

    @Test
    void adminsCanAskAQuestionAndTheRequesterAnswers() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        UUID id = only().getId();

        mvc.perform(admin("ask", id).param("question", "What colour is the strap?"));
        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.NEEDS_INFO);
        assertThat(html("/posts/" + item.getId(), as(STUDENT))).contains("What colour is the strap?");

        mvc.perform(post("/claim-requests/" + id + "/reply").param("reply", "Grey, with a frayed end")
                        .with(as(OTHER_STUDENT)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/claim-requests/" + id + "/reply").param("reply", "Grey, with a frayed end")
                .with(as(STUDENT)).with(csrf()));
        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.PENDING);
        assertThat(only().getInfoReply()).isEqualTo("Grey, with a frayed end");
    }

    @Test
    void denyingNeedsAReasonAndShowsIt() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        UUID id = only().getId();

        mvc.perform(admin("deny", id)).andExpect(flash().attribute("flashError", containsString("reason")));
        mvc.perform(admin("deny", id).param("reason", "The tumbler has no sticker."));

        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.DENIED);
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.POSTED);
        assertThat(html("/posts/" + item.getId(), as(STUDENT))).contains("The tumbler has no sticker.");
        assertThat(html("/posts/" + item.getId(), as(STUDENT))).contains("Send claim request");
    }

    @Test
    void releasingToTheVerifiedRequesterClosesTheOthers() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        mvc.perform(request(item, "It's mine, I left it after PE class on Monday.").with(as(STAFF)).with(csrf()));
        List<ClaimRequest> both = claimRequestRepository.findByItemIdOrderByCreatedAtAsc(item.getId());
        mvc.perform(admin("verify", both.get(0).getId()));

        assertThat(html("/admin/reports/" + item.getId() + "/claim?request=" + both.get(0).getId(), as(ADMIN)))
                .contains(STUDENT.email());
        mvc.perform(post("/admin/reports/" + item.getId() + "/claim").param("claimantEmail", STUDENT.email())
                        .param("releasedAt", localNow().minusMinutes(5).toString()).with(as(ADMIN)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(claimRequestRepository.findById(both.get(0).getId()).orElseThrow().getStatus())
                .isEqualTo(ClaimRequestStatus.RELEASED);
        assertThat(claimRequestRepository.findById(both.get(1).getId()).orElseThrow().getStatus())
                .isEqualTo(ClaimRequestStatus.CLOSED);
        assertThat(items.findById(item.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.CLAIMED);
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.RETURNED);
    }

    @Test
    void anUncollectedPassExpiresAndCanBeExtended() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        UUID id = only().getId();
        mvc.perform(admin("verify", id));

        Instant before = only().getExpiresAt();
        mvc.perform(admin("extend", id));
        assertThat(only().getExpiresAt()).isEqualTo(before.plus(Duration.ofDays(7)));
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_PASS_EXTENDED);
        assertThat(activityLog.findTop10ByOrderByCreatedAtDescIdDesc().subList(0, 2))
                .extracting(ActivityLogEntry::getReason)
                .allSatisfy(reason -> assertThat(reason).containsPattern("\\d:\\d\\d [AP]M").doesNotContain("\u00a0"));

        assertThat(claimRequests.expireDue()).isZero();
        ClaimRequest r = only();
        r.extendPass(Instant.now().minus(Duration.ofMinutes(1)), Instant.now());
        claimRequestRepository.save(r);

        assertThat(claimRequests.expireDue()).isEqualTo(1);
        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.EXPIRED);
        assertThat(lastAction()).isEqualTo(ActivityAction.CLAIM_PASS_EXPIRED);
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.POSTED);
    }

    @Test
    void withdrawingEndsTheRequest() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));
        UUID id = only().getId();

        mvc.perform(post("/claim-requests/" + id + "/withdraw").with(as(STAFF)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/claim-requests/" + id + "/withdraw").with(as(STUDENT)).with(csrf()));

        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.WITHDRAWN);
        assertThat(stageOnFeed(item)).isEqualTo(ItemStage.POSTED);
    }

    @Test
    void removingAnItemClosesItsRequests() throws Exception {
        Item item = found("Blue tumbler");
        mvc.perform(request(item, PROOF).with(as(STUDENT)).with(csrf()));

        mvc.perform(post("/admin/reports/" + item.getId() + "/remove").with(as(ADMIN)).with(csrf()));

        assertThat(only().getStatus()).isEqualTo(ClaimRequestStatus.CLOSED);
    }

    @Test
    void requestPhotosAreOnlyForTheRequesterAndAdmins() throws Exception {
        Item item = found("Blue tumbler");
        ClaimRequest r = claimRequestRepository.save(new ClaimRequest(item.getId(), STUDENT.uid(), STUDENT.email(),
                STUDENT.name(), null, PROOF, "claims/abc.jpg", Instant.now()));

        mvc.perform(get("/claim-requests/" + r.getId() + "/photo").with(as(OTHER_STUDENT)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/claim-requests/" + r.getId() + "/photo").with(as(STAFF)))
                .andExpect(status().isNotFound());
    }

    private Item found(String name) {
        return save(item(name, ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request(
            Item item, String proof) {
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart("/posts/" + item.getId() + "/claim-requests").param("proof", proof);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder admin(String action, UUID id) {
        return post("/admin/claim-requests/" + id + "/" + action).with(as(ADMIN)).with(csrf());
    }

    private ClaimRequest only() {
        assertThat(claimRequestRepository.findAll()).hasSize(1);
        return claimRequestRepository.findAll().get(0);
    }

    private ItemStage stageOnFeed(Item item) {
        return feedIndex.query(new com.foundora.feed.FeedQuery(null, null, null, null, null, false, false, 0)).posts().stream()
                .filter(p -> p.id().equals(item.getId())).findFirst().orElseThrow().stage();
    }

    private ActivityAction lastAction() {
        return activityLog.findTop10ByOrderByCreatedAtDescIdDesc().get(0).getAction();
    }

    private static LocalDateTime localNow() {
        return LocalDateTime.now(ZoneId.of("Asia/Manila")).truncatedTo(ChronoUnit.MINUTES);
    }

    @Test
    void keepsTheRequestWhenThePhotoCannotBeUploaded() throws Exception {
        Item item = found("Blue tumbler");
        when(storage.storePhoto(anyString(), any())).thenThrow(new ResourceAccessException("Supabase Storage unreachable"));

        mvc.perform(request(item, PROOF).file(new MockMultipartFile("photo", "me.png", "image/png", png(300, 200)))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", StorageService.UPLOAD_FAILED))
                .andExpect(flash().attribute("proofDraft", PROOF));

        assertThat(claimRequestRepository.count()).isZero();
    }
}
