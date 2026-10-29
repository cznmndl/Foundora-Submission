package com.foundora.reporting;

import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.Category;
import com.foundora.database.entity.ClaimRequest;
import com.foundora.database.entity.ClaimRequestStatus;
import com.foundora.database.entity.Item;
import com.foundora.database.entity.MoneyContainer;
import com.foundora.database.entity.ReportStatus;
import com.foundora.database.entity.ReportType;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static com.foundora.support.TestData.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Money: found money shows only as "Money" and when it was surrendered; lost money never shows publicly; the
 * amount and other money details never reach search results or public pages. Like LocationPrivacyTest, these
 * check the HTML actually sent, not just what's visible.
 */
class MoneyTest extends WebTestBase {

    // Details that must never leak. The place isn't in the dropdown list, so the report form can't put it on a page.
    private static final String PLACE = "Old Annex Building";
    private static final String SPOT = "Taped under the court 2 scoreboard";
    private static final String DENOMINATIONS = "two orange thousands, seven blue fifties";
    private static final String[] SECRETS = {"1,250.50", "1250.5", "seven blue fifties", PLACE, "scoreboard",
            "1:47 PM", "1:47\u00a0PM", OTHER_STUDENT.name()};

    // ---------- Found money on the board ----------

    @Test
    void foundMoneyCardShowsOnlyMoneyAndWhenItWasSurrendered() throws Exception {
        Instant surrendered = Instant.parse("2026-10-05T02:30:00Z"); // 10:30 AM in Manila
        Item money = money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        money.setReviewedAt(surrendered);
        money = save(money);

        for (RequestPostProcessor user : new RequestPostProcessor[]{as(STUDENT), as(STAFF)}) {
            for (String url : new String[]{"/", "/feed/posts", "/?category=money", "/?type=found",
                    "/posts/" + money.getId()}) {
                String html = html(url, user);
                assertThat(html).as(url).contains("/posts/" + money.getId()).contains(">Money<")
                        .contains("Surrendered")
                        .contains(url.startsWith("/posts/") ? "Oct 5, 2026, 10:30 AM" : "Oct 5, 2026, 10:30\u00a0AM")
                        .doesNotContain("Admin only").doesNotContain("Posted by</dt><dd>Maria");
                for (String secret : SECRETS) {
                    assertThat(html).as(url + " leaks " + secret).doesNotContain(secret);
                }
            }
        }
        // No photo endpoint, no comments: a public "I lost ₱500" would give the answers away.
        assertThat(html("/posts/" + money.getId(), as(STUDENT))).doesNotContain("Write a comment");
        mvc.perform(multipart("/posts/" + money.getId() + "/comments").param("body", "Mine! ₱1,250 in an envelope")
                .with(as(STUDENT)).with(csrf()));
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void searchResultsAndSuggestionsNeverHoldMoneyDetails() throws Exception {
        Item money = save(money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        for (String q : new String[]{"1250", "1,250.50", "orange", "fifties", "envelope", "annex", "scoreboard"}) {
            assertThat(html("/?q=" + q, as(STUDENT))).as(q).doesNotContain("/posts/" + money.getId());
            assertThat(html("/feed/posts?q=" + q, as(STUDENT))).as(q).doesNotContain("/posts/" + money.getId());
        }
        assertThat(html("/?q=money", as(STUDENT))).contains("/posts/" + money.getId());
        for (String prefix : new String[]{"12", "or", "fif", "env", "blu"}) {
            assertThat(html("/api/suggest/search?q=" + prefix, as(STUDENT))).as(prefix).isEqualTo("[]");
        }

        // The admin's All reports search doesn't match or list amounts either.
        assertThat(html("/admin/reports?q=1250", as(ADMIN))).doesNotContain("/posts/" + money.getId());
        assertThat(html("/admin/reports?q=orange", as(ADMIN))).doesNotContain("/posts/" + money.getId());
        assertThat(html("/admin/reports", as(ADMIN))).contains("/posts/" + money.getId())
                .doesNotContain("1,250.50").doesNotContain("seven blue fifties");
    }

    @Test
    void adminsSeeTheMoneyDetails() throws Exception {
        Item money = save(money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));

        for (String url : new String[]{"/", "/posts/" + money.getId()}) {
            assertThat(html(url, as(ADMIN))).as(url).contains("₱1,250.50").contains(DENOMINATIONS)
                    .contains("Envelope").contains(PLACE).contains("1:47 PM");
        }
    }

    // ---------- Lost money ----------

    @Test
    void lostMoneyIsSeenOnlyByItsReporterAndAdmins() throws Exception {
        Item lost = save(money(ReportType.LOST, ReportStatus.APPROVED, OTHER_STUDENT));

        for (String url : new String[]{"/", "/feed/posts", "/?type=lost", "/?category=money", "/?q=money"}) {
            assertThat(html(url, as(STUDENT))).as(url).doesNotContain("/posts/" + lost.getId());
        }
        mvc.perform(get("/posts/" + lost.getId()).with(as(STUDENT))).andExpect(status().isNotFound());
        assertThat(feedIndex.stats().onFeed()).isZero();

        assertThat(html("/posts/" + lost.getId(), as(OTHER_STUDENT))).contains("Not posted on the board")
                .contains("SDFO will contact you if money matching your description is turned in");
        assertThat(html("/my-reports", as(OTHER_STUDENT))).contains("₱1,250.50").contains("Cash is hard to identify");
        assertThat(html("/posts/" + lost.getId(), as(ADMIN))).contains("₱1,250.50").contains(DENOMINATIONS);
    }

    // ---------- Reporting ----------

    @Test
    void foundMoneyIsSavedWithoutPhotoNameOrDescription() throws Exception {
        mvc.perform(moneyReport("FOUND")
                        .file(new MockMultipartFile("photo", "cash.png", "image/png", new byte[]{1, 2, 3}))
                        .param("itemName", "Envelope with ₱500").param("description", "Two ₱500 bills")
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent"))
                .andExpect(flash().attribute("sentMoney", true));

        verify(storage, never()).storePhoto(any());
        assertThat(items.findAll()).singleElement().satisfies(i -> {
            assertThat(i.getCategory()).isEqualTo(Category.MONEY);
            assertThat(i.getItemName()).isEqualTo("Money");
            assertThat(i.getDescription()).isEmpty();
            assertThat(i.getImagePath()).isNull();
            assertThat(i.getMoneyAmount()).isEqualByComparingTo("500");
            assertThat(i.getMoneyContainer()).isEqualTo(MoneyContainer.COIN_PURSE);
            assertThat(i.getMoneyDenominations()).isEqualTo("5 × ₱100");
            assertThat(i.getTimeLostFound()).isEqualTo(LocalTime.of(13, 30));
        });
    }

    @Test
    void moneyNeedsTheAmountAndContainerButNoNameOrDescription() throws Exception {
        mvc.perform(multipart("/report").param("type", "FOUND").param("category", "MONEY")
                        .param("location", "Sentrum").param("locationSpot", "Bench")
                        .param("dateLostFound", today().toString()).with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the amount, e.g. 500.")))
                .andExpect(content().string(containsString("Choose what the money was in.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Enter the item’s name."))));
        mvc.perform(moneyReport("FOUND", "moneyAmount", "lots").with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the amount as a number")));
        mvc.perform(moneyReport("FOUND", "moneyAmount", "0").with(as(STUDENT)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("more than zero")));
        assertThat(items.count()).isZero();
    }

    @Test
    void theFormExplainsWhereWalletsGo() throws Exception {
        assertThat(html("/report", as(STUDENT)))
                .contains("Money is for loose cash, envelopes and coin purses. Wallets go under Bags &amp; Wallets.");
    }

    @Test
    void lostMoneyConfirmationSaysSdfoWillMakeContact() throws Exception {
        var result = mvc.perform(moneyReport("LOST").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/report/sent")).andReturn();
        mvc.perform(get("/report/sent").with(as(STUDENT)).flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString(
                        "Cash is hard to identify. SDFO will contact you if money matching your description is turned in.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("It goes on the board"))));
    }

    // ---------- Claims ----------

    @Test
    void moneyClaimsMustDescribeTheMoneyAndAdminsSeeItBesideTheFindersDetails() throws Exception {
        Item money = save(money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        String url = "/posts/" + money.getId() + "/claim-requests";

        mvc.perform(post(url).param("proof", "It's mine, I lost it on Monday near the library.")
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Enter how much money you lost")));
        mvc.perform(moneyClaim(url, "moneyContainer", "").with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Choose what the money was in")));
        mvc.perform(moneyClaim(url, "moneyWhere", " ").with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Say where you lost the money")));
        mvc.perform(moneyClaim(url, "moneyLostOn", today().plusDays(1).toString()).with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("can't be in the future")));
        assertThat(claimRequestRepository.count()).isZero();

        mvc.perform(moneyClaim(url).with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("Request sent")));
        ClaimRequest request = claimRequestRepository.findAll().get(0);
        assertThat(request.getMoneyAmount()).isEqualByComparingTo("1250.50");
        assertThat(request.getMoneyContainer()).isEqualTo(MoneyContainer.ENVELOPE);
        assertThat(request.getMoneyWhere()).isEqualTo("Annex hallway");
        assertThat(request.getProof()).isEmpty();

        // Admins compare: the claimant's answers and the finder's private details, side by side.
        String claims = html("/admin/claims", as(ADMIN));
        assertThat(claims).contains("Claimant says").contains("Finder reported").contains("Annex hallway")
                .contains(PLACE + " · " + SPOT).contains("Same").contains("₱1,250.50");
        // Nobody else sees the claimant's answers.
        assertThat(html("/posts/" + money.getId(), as(STAFF))).doesNotContain("Annex hallway");
        assertThat(html("/posts/" + money.getId(), as(STUDENT))).contains("Annex hallway");
    }

    // ---------- Handled by SDFO ----------

    @Test
    void unclaimedFoundMoneyCanBeMarkedHandledWithARequiredNote() throws Exception {
        Item money = save(money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        mvc.perform(moneyClaim("/posts/" + money.getId() + "/claim-requests").with(as(STUDENT)).with(csrf()));

        mvc.perform(post("/admin/reports/" + money.getId() + "/handle").param("note", "  ")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Write what SDFO did")));
        assertThat(items.findById(money.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.APPROVED);

        mvc.perform(post("/admin/reports/" + money.getId() + "/handle")
                        .param("note", "Unclaimed after 60 days; turned over to Finance, OR 12345.")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/money"))
                .andExpect(flash().attribute("flashSuccess", containsString("handled by SDFO")));

        Item handled = items.findById(money.getId()).orElseThrow();
        assertThat(handled.getStatus()).isEqualTo(ReportStatus.HANDLED);
        assertThat(handled.getHandledNote()).isEqualTo("Unclaimed after 60 days; turned over to Finance, OR 12345.");
        assertThat(handled.getHandledByName()).isEqualTo(ADMIN.name());
        assertThat(activityLog.findAll()).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(ActivityAction.HANDLED_BY_SDFO);
            assertThat(e.getReason()).contains("OR 12345");
            assertThat(e.getActorEmail()).isEqualTo(ADMIN.email());
        });
        assertThat(claimRequestRepository.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(ClaimRequestStatus.CLOSED));
        assertThat(html("/", as(STUDENT))).doesNotContain("/posts/" + money.getId());
        assertThat(html("/admin/activity?action=handled-by-sdfo", as(ADMIN))).contains("Handled").contains("OR 12345");

        // Final: it can't be handled again, and other items can't be handled at all.
        mvc.perform(post("/admin/reports/" + money.getId() + "/handle").param("note", "Again")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("can't be marked as handled")));
        Item umbrella = save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT));
        mvc.perform(post("/admin/reports/" + umbrella.getId() + "/handle").param("note", "Donated")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("Only found money")));
        mvc.perform(post("/admin/reports/" + umbrella.getId() + "/handle").param("note", "Donated")
                .with(as(STUDENT)).with(csrf())).andExpect(status().isForbidden());
    }

    // ---------- The admin's Lost money view ----------

    @Test
    void lostMoneyViewComparesLostAndFoundByAmountAndDate() throws Exception {
        Item lost = money(ReportType.LOST, ReportStatus.APPROVED, STUDENT);
        lost.setMoneyAmount(new BigDecimal("500"));
        lost.setDateLostFound(today().minusDays(3));
        lost = save(lost);
        Item close = money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        close.setMoneyAmount(new BigDecimal("450"));
        close.setDateLostFound(today().minusDays(1));
        close = save(close);
        Item tooMuch = money(ReportType.FOUND, ReportStatus.APPROVED, OTHER_STUDENT);
        tooMuch.setMoneyAmount(new BigDecimal("2000"));
        tooMuch.setDateLostFound(today().minusDays(1));
        tooMuch = save(tooMuch);

        String html = html("/admin/money", as(ADMIN));
        assertThat(html).contains("Lost money reports (1)").contains("Found money held (2)")
                .contains("₱500.00").contains("₱450.00").contains("₱2,000.00").contains("Handled by SDFO");
        String matches = html.substring(html.indexOf("Possible matches"), html.indexOf("found-heading"));
        assertThat(matches).contains("/posts/" + close.getId()).doesNotContain("/posts/" + tooMuch.getId());

        String byAmount = html("/admin/money?sort=amount", as(ADMIN));
        assertThat(byAmount.indexOf("₱2,000.00")).isLessThan(byAmount.lastIndexOf("₱450.00"));

        mvc.perform(get("/admin/money").with(as(STUDENT))).andExpect(status().isForbidden());
        assertThat(html("/admin/money", as(ADMIN))).contains("/posts/" + lost.getId());
    }

    // ---------- Helpers ----------

    private Item money(ReportType type, ReportStatus status, com.foundora.auth.FoundoraUser reporter) {
        // Sent at 9:00 AM Manila time, so no time on the page can be mistaken for the private 1:47 PM.
        Item item = item("Money", type, status, reporter, Instant.parse("2026-10-05T01:00:00Z"));
        item.setCategory(Category.MONEY);
        item.setDescription("");
        item.setLocation(PLACE);
        item.setLocationSpot(SPOT);
        item.setMoneyAmount(new BigDecimal("1250.50"));
        item.setMoneyDenominations(DENOMINATIONS);
        item.setMoneyContainer(MoneyContainer.ENVELOPE);
        item.setTimeLostFound(LocalTime.of(13, 47));
        item.setDateLostFound(today().minusDays(2));
        return item;
    }

    /** A complete money report; {@code overrides} are name/value pairs that replace a field. */
    private static MockMultipartHttpServletRequestBuilder moneyReport(String type, String... overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("type", type);
        fields.put("category", "MONEY");
        fields.put("moneyAmount", "500");
        fields.put("moneyContainer", "COIN_PURSE");
        fields.put("moneyDenominations", "5 × ₱100");
        fields.put("timeLostFound", "13:30");
        fields.put("location", "Sentrum");
        fields.put("locationSpot", "Bench beside the vending machine");
        fields.put("dateLostFound", today().toString());
        MockMultipartHttpServletRequestBuilder request = multipart("/report");
        override(fields, overrides).forEach(request::param);
        return request;
    }

    /** A complete money claim; {@code overrides} are name/value pairs that replace a field. */
    private static MockHttpServletRequestBuilder moneyClaim(String url, String... overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("moneyAmount", "1,250.50");
        fields.put("moneyContainer", "ENVELOPE");
        fields.put("moneyDenominations", "two thousands");
        fields.put("moneyWhere", "Annex hallway");
        fields.put("moneyLostOn", today().minusDays(2).toString());
        fields.put("moneyLostTime", "13:00");
        MockHttpServletRequestBuilder request = post(url);
        override(fields, overrides).forEach(request::param);
        return request;
    }

    private static Map<String, String> override(Map<String, String> fields, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            fields.put(pairs[i], pairs[i + 1]);
        }
        return fields;
    }
}
