package com.foundora.accounts;

import com.foundora.auth.FoundoraUser;
import com.foundora.database.entity.ActivityAction;
import com.foundora.database.entity.ActivityLogEntry;
import com.foundora.database.entity.LoginAttempt;
import com.foundora.database.entity.UserProfile;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.beforePin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin's Accounts page: resetting PINs, unlocking accounts, and that only admins can. */
class AccountTest extends WebTestBase {

    @Autowired
    private AccountService accounts;

    @Test
    void anAdminResetsAPinAndTheStudentCreatesANewOneAtNextSignIn() throws Exception {
        givenPin(STUDENT, "2580");
        mvc.perform(get("/admin/accounts").param("q", "juan").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Reset PIN for Juan Dela Cruz? They’ll create a new PIN on their next sign-in.")));

        mvc.perform(post("/admin/accounts/reset-pin").param("uid", STUDENT.uid()).param("q", "juan")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts?q=juan"))
                .andExpect(flash().attribute("flashSuccess", containsString("Reset the PIN for Juan Dela Cruz")));

        assertThat(profile(STUDENT).hasPin()).isFalse();
        assertThat(loginRows()).containsExactly("PIN_RESET " + STUDENT.uid() + " " + STUDENT.email());
        ActivityLogEntry entry = lastActivity();
        assertThat(entry.getAction()).isEqualTo(ActivityAction.PIN_RESET);
        assertThat(entry.getActorEmail()).isEqualTo(ADMIN.email());
        assertThat(entry.getActorName()).isEqualTo(ADMIN.name());
        assertThat(entry.getItemName()).isEqualTo("Juan Dela Cruz (" + STUDENT.email() + ")");
        assertThat(entry.getItemId()).isNull();

        mvc.perform(get("/pin").session(new MockHttpSession()).with(beforePin(STUDENT)))
                .andExpect(redirectedUrl("/pin/setup"));
        mvc.perform(get("/admin/activity").with(as(ADMIN)))
                .andExpect(content().string(containsString("reset the PIN for")));
    }

    @Test
    void aLockedAccountMustBeUnlockedBeforeItsPinCanBeReset() throws Exception {
        givenPin(STUDENT, "2580");
        Instant until = Instant.now().plus(Duration.ofHours(20)).truncatedTo(ChronoUnit.SECONDS);
        lock(STUDENT, until);

        mvc.perform(get("/admin/accounts").with(as(ADMIN)))
                .andExpect(content().string(containsString("Locked accounts (1)")))
                .andExpect(content().string(containsString("Unlock account")));

        mvc.perform(post("/admin/accounts/reset-pin").param("uid", STUDENT.uid()).with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attribute("flashError", containsString("This account is locked until ")))
                .andExpect(flash().attribute("flashError", containsString(". Unlock it first.")));
        UserProfile stillLocked = profile(STUDENT);
        assertThat(stillLocked.hasPin()).isTrue();
        assertThat(stillLocked.getPinLockedUntil()).isEqualTo(until);
        assertThat(stillLocked.getPinFailedAttempts()).isEqualTo(3);
        assertThat(loginRows()).isEmpty();
        assertThat(activityLog.count()).isZero();

        mvc.perform(post("/admin/accounts/unlock").param("uid", STUDENT.uid()).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", "Unlocked Juan Dela Cruz's account."));
        UserProfile unlocked = profile(STUDENT);
        assertThat(unlocked.getPinLockedUntil()).isNull();
        assertThat(unlocked.getPinFailedAttempts()).isZero();
        assertThat(unlocked.hasPin()).isTrue();
        assertThat(lastActivity().getAction()).isEqualTo(ActivityAction.ACCOUNT_UNLOCKED);
        assertThat(lastActivity().getActorEmail()).isEqualTo(ADMIN.email());

        mvc.perform(post("/admin/accounts/reset-pin").param("uid", STUDENT.uid()).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", containsString("Reset the PIN")));
        assertThat(loginRows()).containsExactly(
                "ACCOUNT_UNLOCKED " + STUDENT.uid() + " " + STUDENT.email(),
                "PIN_RESET " + STUDENT.uid() + " " + STUDENT.email());
    }

    @Test
    void onlyLockedAccountsCanBeUnlockedAndOnlySetPinsReset() throws Exception {
        mvc.perform(post("/admin/accounts/reset-pin").param("uid", STUDENT.uid()).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", containsString("has no PIN yet")));
        givenPin(STUDENT, "2580");
        mvc.perform(post("/admin/accounts/unlock").param("uid", STUDENT.uid()).with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", "Juan Dela Cruz's account is not locked."));
        mvc.perform(post("/admin/accounts/unlock").param("uid", "uid-nobody").with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashError", "That account no longer exists."));
        assertThat(loginRows()).isEmpty();
        assertThat(activityLog.count()).isZero();
    }

    @Test
    void studentsAndStaffCannotResetOrUnlockEvenByPostingDirectly() throws Exception {
        givenPin(STUDENT, "2580");
        lock(STUDENT, Instant.now().plus(Duration.ofHours(5)));
        for (FoundoraUser user : new FoundoraUser[]{STUDENT, STAFF}) {
            mvc.perform(get("/admin/accounts").with(as(user))).andExpect(status().isForbidden());
            for (String action : new String[]{"reset-pin", "unlock"}) {
                mvc.perform(post("/admin/accounts/" + action).param("uid", STUDENT.uid()).with(as(user)).with(csrf()))
                        .andExpect(status().isForbidden());
            }
            assertThatThrownBy(() -> accounts.resetPin(user, STUDENT.uid())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> accounts.unlock(user, STUDENT.uid())).isInstanceOf(AccessDeniedException.class);
        }
        mvc.perform(post("/admin/accounts/reset-pin").param("uid", STUDENT.uid()).with(as(ADMIN)))
                .andExpect(status().isForbidden());

        UserProfile untouched = profile(STUDENT);
        assertThat(untouched.hasPin()).isTrue();
        assertThat(untouched.getPinLockedUntil()).isNotNull();
        assertThat(loginRows()).isEmpty();
        assertThat(activityLog.count()).isZero();
    }

    @Test
    void theAccountsPageIsLinkedFromTheAdminNavAndOverview() throws Exception {
        mvc.perform(get("/admin").with(as(ADMIN)))
                .andExpect(content().string(containsString("href=\"/admin/accounts\"")));
        mvc.perform(get("/admin/accounts").with(as(ADMIN)))
                .andExpect(content().string(containsString("No accounts are locked right now.")))
                .andExpect(content().string(not(containsString("Reset PIN for"))));
    }

    @Test
    void everyAccountCanBeFoundByPartOfItsNameOrEmail() throws Exception {
        profileRepository.save(new UserProfile("uid-james", "james_constantine_macasaet@dlsl.edu.ph",
                "JAMES CONSTANTINE MACASAET", com.foundora.auth.UserRole.STUDENT, Instant.now()));
        profileRepository.save(new UserProfile("uid-diosa", "diosa_mae_decastro@dlsl.edu.ph",
                "DIOSA MAE DE CASTRO", com.foundora.auth.UserRole.STUDENT, Instant.now()));
        profileRepository.save(new UserProfile("uid-leila", "leila.tura@dlsl.edu.ph", "Leila Tura",
                com.foundora.auth.UserRole.STAFF, Instant.now()));
        givenPin(STUDENT, "2580");

        java.util.Map<String, String> searches = new java.util.LinkedHashMap<>();
        searches.put("james", "JAMES CONSTANTINE MACASAET");
        searches.put("macasaet", "JAMES CONSTANTINE MACASAET");
        searches.put("James Macasaet", "JAMES CONSTANTINE MACASAET");
        searches.put("macasaet james", "JAMES CONSTANTINE MACASAET");
        searches.put("  JAMES_constantine  ", "JAMES CONSTANTINE MACASAET");
        searches.put("de castro", "DIOSA MAE DE CASTRO");
        searches.put("decastro", "DIOSA MAE DE CASTRO");
        searches.put("leila.tura", "Leila Tura");
        searches.put("leila.tura@dlsl.edu.ph", "Leila Tura");
        searches.put("Tura Leila", "Leila Tura");
        searches.put("ben.reyes", STAFF.name());
        searches.put("Dela Cruz", STUDENT.name());
        for (var search : searches.entrySet()) {
            assertThat(accounts.search(search.getKey())).as(search.getKey())
                    .extracting(a -> a.profile().getFullName()).contains(search.getValue());
        }
        assertThat(accounts.search("james leila")).isEmpty();
        assertThat(accounts.search("macasaet")).hasSize(1);

        mvc.perform(get("/admin/accounts").param("q", "James Macasaet").with(as(ADMIN)))
                .andExpect(content().string(containsString("JAMES CONSTANTINE MACASAET")))
                .andExpect(content().string(containsString("Not set yet")));
    }

    private void givenPin(FoundoraUser user, String pin) {
        UserProfile p = profile(user);
        p.setPinHash(new BCryptPasswordEncoder().encode(pin));
        profileRepository.save(p);
    }

    private void lock(FoundoraUser user, Instant until) {
        UserProfile p = profile(user);
        p.setPinFailedAttempts(3);
        p.setPinLockedUntil(until);
        profileRepository.save(p);
    }

    private UserProfile profile(FoundoraUser user) {
        return profileRepository.findById(user.uid()).orElseThrow();
    }

    private java.util.List<String> loginRows() {
        return loginAttempts.findAll().stream()
                .sorted(java.util.Comparator.comparing(LoginAttempt::getId))
                .map(a -> a.getOutcome() + " " + a.getUid() + " " + a.getEmail())
                .toList();
    }

    private ActivityLogEntry lastActivity() {
        return activityLog.findTop10ByOrderByCreatedAtDescIdDesc().get(0);
    }
}
