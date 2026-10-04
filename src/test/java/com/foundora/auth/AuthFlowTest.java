package com.foundora.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.ErrorCode;
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
        mvc.perform(post("/admin/approve/1").session(session).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void allowlistedAdminGoesToAdmin() throws Exception {
        MockHttpSession session = signIn("admin-token", token("ana.cruz@dlsl.edu.ph", true, "google.com"), "/admin");

        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
    }

    @Test
    void rejectsDisallowedAccounts() throws Exception {
        assertRejected(token("ben.santos@dlsl.edu.ph", true, "google.com"));   // dot email, not allowlisted
        assertRejected(token("juan_delacruz@gmail.com", true, "google.com"));  // wrong domain
        assertRejected(token("juan_delacruz@dlsl.edu.ph", false, "google.com")); // unverified email
        assertRejected(token("juan_delacruz@dlsl.edu.ph", true, "password"));  // not Google sign-in
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
}
