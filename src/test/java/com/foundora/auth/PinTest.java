package com.foundora.auth;

import com.foundora.database.entity.UserProfile;
import com.foundora.support.TestData;
import com.foundora.support.WebTestBase;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.foundora.support.TestData.beforePin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/** The PIN step after Google sign-in: setup, entry, the 24-hour lock, and what the login history records. */
class PinTest extends WebTestBase {

    private static final FoundoraUser USER = TestData.STUDENT;

    @Test
    void rejectsPinsThatAreNotFourDigitsEasyOrMismatched() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Map<String, String> bad = Map.of(
                "", "exactly 4 digits", "258", "exactly 4 digits", "25801", "exactly 4 digits",
                "25a0", "exactly 4 digits", "1111", "too easy", "1234", "too easy", "0123", "too easy",
                "9876", "too easy");
        for (var entry : bad.entrySet()) {
            mvc.perform(post("/pin/setup").session(session).with(beforePin(USER)).with(csrf())
                            .param("pin", entry.getKey()).param("confirm", entry.getKey()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(entry.getValue())));
        }
        mvc.perform(post("/pin/setup").session(session).with(beforePin(USER)).with(csrf())
                        .param("pin", "2580").param("confirm", "2581"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("don&#39;t match")));
        assertThat(profile().hasPin()).isFalse();
        mvc.perform(get("/").session(session).with(beforePin(USER))).andExpect(redirectedUrl("/pin"));
    }

    @Test
    void storesOnlyABcryptHashAndThenOpensTheApp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/pin/setup").session(session).with(beforePin(USER)).with(csrf())
                        .param("pin", "2580").param("confirm", "2580"))
                .andExpect(redirectedUrl("/"));

        assertThat(profile().getPinHash()).startsWith("$2").doesNotContain("2580");
        mvc.perform(get("/").session(session).with(beforePin(USER))).andExpect(status().isOk());
        mvc.perform(get("/pin/setup").session(session).with(beforePin(USER))).andExpect(redirectedUrl("/"));
    }

    @Test
    void aSecondSetupCannotReplaceAnExistingPin() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/pin/setup").session(session).with(beforePin(USER))).andExpect(redirectedUrl("/pin"));
        mvc.perform(post("/pin/setup").session(session).with(beforePin(USER)).with(csrf())
                        .param("pin", "1357").param("confirm", "1357"))
                .andExpect(redirectedUrl("/pin"));
        mvc.perform(get("/").session(session).with(beforePin(USER))).andExpect(redirectedUrl("/pin"));
        assertThat(correct("2580")).isTrue();
    }

    @Test
    void everyPageWaitsForThePinExceptPublicOnes() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();
        for (String page : new String[]{"/", "/report", "/my-reports", "/profile", "/profile/setup", "/feed/posts",
                "/api/suggest/search?q=um"}) {
            mvc.perform(get(page).session(session).with(beforePin(USER)))
                    .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/pin"));
        }
        mvc.perform(get("/admin/pending").session(session).with(beforePin(TestData.ADMIN)))
                .andExpect(redirectedUrl("/pin"));
        mvc.perform(post("/report").session(session).with(beforePin(USER)).with(csrf()))
                .andExpect(redirectedUrl("/pin"));
        for (String open : new String[]{"/pin", "/health", "/css/app.css", "/fonts/inter-latin-wght-italic.woff2",
                "/favicon.svg"}) {
            mvc.perform(get(open).session(session).with(beforePin(USER))).andExpect(status().isOk());
        }
        mvc.perform(get("/login").session(session).with(beforePin(USER))).andExpect(status().is3xxRedirection());
    }

    @Test
    void threeWrongPinsLockEntryForADay() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();

        wrong(session).andExpect(flash().attribute("pinError", "Incorrect. 2 attempts left."));
        wrong(session).andExpect(flash().attribute("pinError", "Incorrect. 1 attempt left."));
        Instant before = Instant.now();
        wrong(session);

        UserProfile locked = profile();
        assertThat(locked.getPinFailedAttempts()).isEqualTo(3);
        assertThat(locked.getPinLockedUntil()).isBetween(before.plus(Duration.ofHours(24)).minusSeconds(5),
                Instant.now().plus(Duration.ofHours(24)).plusSeconds(5));

        mvc.perform(get("/pin").session(session).with(beforePin(USER)))
                .andExpect(content().string(containsString("Too many wrong PINs. Try again after ")))
                .andExpect(content().string(not(containsString("id=\"pin\""))))
                .andExpect(content().string(not(containsString("Forgot your PIN?"))));

        mvc.perform(post("/pin").session(session).with(beforePin(USER)).with(csrf()).param("pin", "2580"))
                .andExpect(redirectedUrl("/pin"));
        mvc.perform(get("/").session(session).with(beforePin(USER))).andExpect(redirectedUrl("/pin"));
    }

    @Test
    void theLockEndsAfterADayWithAllThreeAttemptsBack() throws Exception {
        givenPin("2580");
        UserProfile p = profile();
        p.setPinFailedAttempts(3);
        p.setPinLockedUntil(Instant.now().minusSeconds(1));
        profileRepository.save(p);

        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/pin").session(session).with(beforePin(USER)))
                .andExpect(content().string(containsString("id=\"pin\"")));
        wrong(session).andExpect(flash().attribute("pinError", "Incorrect. 2 attempts left."));
        assertThat(profile().getPinLockedUntil()).isNull();
    }

    @Test
    void theRightPinResetsTheCount() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();
        wrong(session);
        wrong(session);
        mvc.perform(post("/pin").session(session).with(beforePin(USER)).with(csrf()).param("pin", "2580"))
                .andExpect(redirectedUrl("/"));
        assertThat(profile().getPinFailedAttempts()).isZero();
        mvc.perform(get("/").session(session).with(beforePin(USER))).andExpect(status().isOk());
    }

    @Test
    void anIncompleteProfileIsSetUpRightAfterThePin() throws Exception {
        UserProfile p = profile();
        p.setSection(null);
        profileRepository.save(p);
        mvc.perform(post("/pin/setup").session(new MockHttpSession()).with(beforePin(USER)).with(csrf())
                        .param("pin", "2580").param("confirm", "2580"))
                .andExpect(redirectedUrl("/profile/setup"));
    }

    @Test
    void aForgottenPinCanOnlyBeResetAtTheSdfo() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/pin").session(session).with(beforePin(USER)))
                .andExpect(content().string(containsString(
                        "Forgot your PIN? Visit the SDFO and present your school ID to have it reset.")))
                .andExpect(content().string(not(containsString("/pin/forgot"))));

        mvc.perform(post("/pin/forgot").session(session).with(beforePin(USER)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/pin/forgot").session(new MockHttpSession()).with(TestData.as(USER)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(session.isInvalid()).isFalse();
        assertThat(correct("2580")).isTrue();

        FirebaseToken token = token(USER);
        when(firebaseAuth.verifyIdToken(eq("again"), eq(true))).thenReturn(token);
        mvc.perform(post("/auth/session").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"again\"}"))
                .andExpect(jsonPath("$.redirect").value("/pin"));
        assertThat(correct("2580")).isTrue();
    }

    @Test
    void bothPinPagesOfferSignOut() throws Exception {
        mvc.perform(get("/pin/setup").session(new MockHttpSession()).with(beforePin(USER)))
                .andExpect(content().string(containsString("Not you?")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
        givenPin("2580");
        mvc.perform(get("/pin").session(new MockHttpSession()).with(beforePin(USER)))
                .andExpect(content().string(containsString("Welcome back, Juan!")))
                .andExpect(content().string(containsString("Not you?")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
    }

    @Test
    void aReplacedProfileKeepsItsPin() throws Exception {
        givenPin("2580");
        UserProfile p = profile();
        p.setPinFailedAttempts(2);
        profileRepository.save(p);
        String oldHash = p.getPinHash();

        FoundoraUser sameEmailNewUid = new FoundoraUser("uid-juan-new", USER.email(), USER.name(), USER.role());
        FirebaseToken token = token(sameEmailNewUid);
        when(firebaseAuth.verifyIdToken(eq("new-uid"), eq(true))).thenReturn(token);
        mvc.perform(post("/auth/session").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"new-uid\"}"))
                .andExpect(jsonPath("$.redirect").value("/pin"));

        UserProfile moved = profileRepository.findById("uid-juan-new").orElseThrow();
        assertThat(moved.getPinHash()).isEqualTo(oldHash);
        assertThat(moved.getPinFailedAttempts()).isEqualTo(2);
        assertThat(profileRepository.findById(USER.uid())).isEmpty();
    }

    @Test
    void easyPinRules() {
        assertThat(PinService.problem("2580", "2580")).isNull();
        assertThat(PinService.problem("1357", "1357")).isNull();
        assertThat(PinService.problem("1122", "1122")).isNull();
        for (String easy : new String[]{"0000", "7777", "1234", "6789", "3456", "4321", "9876"}) {
            assertThat(PinService.problem(easy, easy)).as(easy).contains("too easy");
        }
        assertThat(PinService.problem("２５８０", "２５８０")).contains("exactly 4 digits");
    }

    @Test
    void recordsEachPinTryWithItsNumberAndTheLastLogin() throws Exception {
        mvc.perform(post("/pin/setup").session(new MockHttpSession()).with(beforePin(USER)).with(csrf())
                .param("pin", "2580").param("confirm", "2580"));
        assertThat(profile().getLastLoginAt()).isNull();

        MockHttpSession session = new MockHttpSession();
        wrong(session);
        Instant before = Instant.now();
        mvc.perform(post("/pin").session(session).with(beforePin(USER)).with(csrf()).param("pin", "2580"))
                .andExpect(redirectedUrl("/"));

        assertThat(history()).containsExactly("PIN_SET:-", "PIN_FAILED:1", "PIN_SUCCESS:2");
        assertThat(loginAttempts.findAll()).allSatisfy(a -> assertThat(a.getUid()).isEqualTo(USER.uid()));
        assertThat(profile().getLastLoginAt()).isBetween(before, Instant.now());
    }

    @Test
    void recordsTheLockAndEveryTryWhileLocked() throws Exception {
        givenPin("2580");
        MockHttpSession session = new MockHttpSession();
        wrong(session);
        wrong(session);
        wrong(session);
        mvc.perform(post("/pin").session(session).with(beforePin(USER)).with(csrf()).param("pin", "2580"))
                .andExpect(redirectedUrl("/pin"));

        assertThat(history()).containsExactly("PIN_FAILED:1", "PIN_FAILED:2", "LOCKED:3", "LOCKED:-");
        assertThat(profile().getLastLoginAt()).isNull();
    }

    private java.util.List<String> history() {
        return loginAttempts.findByEmailOrderByCreatedAtAscIdAsc(USER.email()).stream()
                .map(a -> a.getOutcome() + ":" + (a.getAttemptNumber() == null ? "-" : a.getAttemptNumber()))
                .toList();
    }

    private org.springframework.test.web.servlet.ResultActions wrong(MockHttpSession session) throws Exception {
        return mvc.perform(post("/pin").session(session).with(beforePin(USER)).with(csrf()).param("pin", "9513"))
                .andExpect(redirectedUrl("/pin"));
    }

    private void givenPin(String pin) {
        UserProfile p = profile();
        p.setPinHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(pin));
        profileRepository.save(p);
    }

    private boolean correct(String pin) {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches(pin, profile().getPinHash());
    }

    private UserProfile profile() {
        return profileRepository.findById(USER.uid()).orElseThrow();
    }

    private static FirebaseToken token(FoundoraUser user) {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn(user.uid());
        when(token.getEmail()).thenReturn(user.email());
        when(token.getName()).thenReturn(user.name());
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getClaims()).thenReturn(Map.of("firebase", Map.of("sign_in_provider", "google.com"),
                "auth_time", Instant.now().getEpochSecond()));
        return token;
    }
}
