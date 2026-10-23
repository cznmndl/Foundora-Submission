package com.foundora.auth;

import com.foundora.database.entity.UserProfile;
import com.foundora.database.repository.UserProfileRepository;
import com.google.firebase.ErrorCode;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "foundora.auth.admin-emails=ana.cruz@dlsl.edu.ph")
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FirebaseAuth firebaseAuth;

    @Autowired
    private UserProfileRepository profiles;

    @BeforeEach
    void completedProfiles() {
        profiles.deleteAll();
        profiles.save(profile("juan_delacruz@dlsl.edu.ph", UserRole.STUDENT, "BS Psychology", 1, "O1A", null));
        profiles.save(profile("ana.cruz@dlsl.edu.ph", UserRole.ADMIN, null, null, null, "SDFO"));
        profiles.save(profile("ben.santos@dlsl.edu.ph", UserRole.STAFF, null, null, null, "Library"));
        profiles.save(profile("registrar@dlsl.edu.ph", UserRole.STAFF, null, null, null, "Registrar"));
    }

    @Test
    void firstSignInMustSetUpTheProfileBeforeAnythingElse() throws Exception {
        MockHttpSession session = signIn("new-token", token("pedro_reyes@dlsl.edu.ph", true, "google.com"),
                "/profile/setup");

        for (String page : new String[]{"/", "/my-reports", "/report", "/posts/" + java.util.UUID.randomUUID()}) {
            mvc.perform(get(page).session(session)).andExpect(status().isSeeOther())
                    .andExpect(redirectedUrl("/profile/setup"));
        }
        mvc.perform(post("/report").session(session).with(csrf()))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/profile/setup"));
        mvc.perform(get("/profile/setup").session(session)).andExpect(status().isOk());

        mvc.perform(post("/profile").session(session).with(csrf()).param("community", "COLLEGE")
                        .param("course", "College of Nursing"))
                .andExpect(status().isOk());
        assertThat(profiles.findById("uid-pedro_reyes@dlsl.edu.ph")).get()
                .satisfies(p -> assertThat(p.isCompleteFor(UserRole.STUDENT)).isFalse());

        mvc.perform(post("/profile").session(session).with(csrf())
                        .param("community", "COLLEGE").param("course", "College of Nursing")
                        .param("yearLevel", "2").param("section", "n2a"))
                .andExpect(redirectedUrl("/"));
        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        assertThat(profiles.findById("uid-pedro_reyes@dlsl.edu.ph")).get().satisfies(p -> {
            assertThat(p.getCourse()).isEqualTo("College of Nursing");
            assertThat(p.getYearLevel()).isEqualTo(2);
            assertThat(p.getSection()).isEqualTo("N2A");
            assertThat(p.getEmail()).isEqualTo("pedro_reyes@dlsl.edu.ph");
        });
    }

    @Test
    void staffAndAdminsGiveADepartmentInstead() throws Exception {
        MockHttpSession session = signIn("staff-new", token("lina.garcia@dlsl.edu.ph", true, "google.com"),
                "/profile/setup");

        mvc.perform(post("/profile").session(session).with(csrf()).param("department", "  "))
                .andExpect(status().isOk());
        mvc.perform(post("/profile").session(session).with(csrf()).param("department", "Guidance Office"))
                .andExpect(redirectedUrl("/"));
        assertThat(profiles.findById("uid-lina.garcia@dlsl.edu.ph")).get()
                .satisfies(p -> assertThat(p.getDepartment()).isEqualTo("Guidance Office"));
    }

    @Test
    void profileIsNotAskedForAgainOnTheNextSignIn() throws Exception {
        signIn("t1", token("pedro_reyes@dlsl.edu.ph", true, "google.com"), "/profile/setup");
        UserProfile p = profiles.findById("uid-pedro_reyes@dlsl.edu.ph").orElseThrow();
        p.setCourse("BS Nursing");
        p.setYearLevel(1);
        p.setSection("N1A");
        profiles.save(p);

        signIn("t2", token("pedro_reyes@dlsl.edu.ph", true, "google.com"), "/");
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @Test
    void studentGoesHomeAndIsBlockedFromAdmin() throws Exception {
        MockHttpSession session = signIn("student-token", token("juan_delacruz@dlsl.edu.ph", true, "google.com"), "/");

        mvc.perform(get("/").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reports/" + java.util.UUID.randomUUID() + "/approve").session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowlistedAdminGoesToAdmin() throws Exception {
        MockHttpSession session = signIn("admin-token", token("ana.cruz@dlsl.edu.ph", true, "google.com"), "/admin");

        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
    }

    @Test
    void staffAccountsGoHomeAndAreBlockedFromAdmin() throws Exception {
        MockHttpSession dotStaff = signIn("staff-token", token("ben.santos@dlsl.edu.ph", true, "google.com"), "/");
        mvc.perform(get("/").session(dotStaff)).andExpect(status().isOk());
        mvc.perform(get("/admin").session(dotStaff)).andExpect(status().isForbidden());

        MockHttpSession office = signIn("office-token", token("registrar@dlsl.edu.ph", true, "google.com"), "/");
        mvc.perform(get("/admin/pending").session(office)).andExpect(status().isForbidden());
    }

    @Test
    void rejectsDisallowedAccounts() throws Exception {
        assertRejected(token("juan_delacruz@gmail.com", true, "google.com"));
        assertRejected(token("juan_delacruz@dlsl.edu.ph", false, "google.com"));
        assertRejected(token("juan_delacruz@dlsl.edu.ph", true, "password"));
    }

    @Test
    void rejectsStaleSignIn() throws Exception {
        FirebaseToken stale = token("juan_delacruz@dlsl.edu.ph", true, "google.com",
                Instant.now().minusSeconds(3600));
        assertRejected(stale);
    }

    @Test
    void rejectsTokensFirebaseCannotVerify() throws Exception {
        when(firebaseAuth.verifyIdToken(eq("forged"), eq(true))).thenThrow(
                new FirebaseAuthException(ErrorCode.INVALID_ARGUMENT, "bad signature", null, null,
                        AuthErrorCode.REVOKED_ID_TOKEN));

        mvc.perform(post("/auth/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"forged\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessionExchangeRequiresCsrfToken() throws Exception {
        mvc.perform(post("/auth/session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"anything\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutEndsSession() throws Exception {
        MockHttpSession session = signIn("student-token", token("juan_delacruz@dlsl.edu.ph", true, "google.com"), "/");

        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/").session(session)).andExpect(redirectedUrl("/login"));
    }

    private MockHttpSession signIn(String idToken, FirebaseToken token, String expectedRedirect) throws Exception {
        when(firebaseAuth.verifyIdToken(eq(idToken), eq(true))).thenReturn(token);
        return (MockHttpSession) mvc.perform(post("/auth/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value(expectedRedirect))
                .andReturn().getRequest().getSession(false);
    }

    private void assertRejected(FirebaseToken token) throws Exception {
        when(firebaseAuth.verifyIdToken(eq("rejected-token"), eq(true))).thenReturn(token);
        mvc.perform(post("/auth/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"rejected-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    private static UserProfile profile(String email, UserRole role, String course, Integer year, String section,
                                       String department) {
        UserProfile p = new UserProfile("uid-" + email, email, "Test User", role, Instant.now());
        p.setCourse(course);
        p.setYearLevel(year);
        p.setSection(section);
        p.setDepartment(department);
        return p;
    }

    private static FirebaseToken token(String email, boolean verified, String provider) {
        return token(email, verified, provider, Instant.now());
    }

    private static FirebaseToken token(String email, boolean verified, String provider, Instant authTime) {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("uid-" + email);
        when(token.getEmail()).thenReturn(email);
        when(token.getName()).thenReturn("Test User");
        when(token.isEmailVerified()).thenReturn(verified);
        when(token.getClaims()).thenReturn(Map.of(
                "firebase", Map.of("sign_in_provider", provider),
                "auth_time", authTime.getEpochSecond()));
        return token;
    }

    @Test
    void adminEmailMatchesWhateverItsCapitalization() throws Exception {
        MockHttpSession session = signIn("caps-token", token("Ana.Cruz@DLSL.edu.ph", true, "google.com"), "/admin");

        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredTokens() throws Exception {
        when(firebaseAuth.verifyIdToken(eq("expired"), eq(true))).thenThrow(
                new FirebaseAuthException(ErrorCode.UNAUTHENTICATED, "Firebase ID token has expired", null, null,
                        AuthErrorCode.EXPIRED_ID_TOKEN));

        mvc.perform(post("/auth/session").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"expired\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }
}
