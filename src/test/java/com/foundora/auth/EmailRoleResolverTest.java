package com.foundora.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailRoleResolverTest {

    private final EmailRoleResolver resolver =
            new EmailRoleResolver(List.of(" Ana.Cruz@DLSL.edu.ph ", "sdfo@dlsl.edu.ph"));

    @Test
    void underscoreEmailIsStudent() {
        assertThat(resolver.resolve("juan_delacruz@dlsl.edu.ph")).isEqualTo(UserRole.STUDENT);
        assertThat(resolver.resolve("Juan_DelaCruz@DLSL.EDU.PH")).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void allowlistedDotEmailIsAdmin() {
        assertThat(resolver.resolve("ana.cruz@dlsl.edu.ph")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void dotEmailNotOnAllowlistIsStaff() {
        assertThat(resolver.resolve("ben.santos@dlsl.edu.ph")).isEqualTo(UserRole.STAFF);
    }

    @Test
    void officeAccountIsStaffUnlessAllowlisted() {
        assertThat(resolver.resolve("registrar@dlsl.edu.ph")).isEqualTo(UserRole.STAFF);
        assertThat(resolver.resolve("SDFO@dlsl.edu.ph")).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void allowlistWinsOverEmailFormat() {
        EmailRoleResolver withStudentAdmin = new EmailRoleResolver(List.of("juan_delacruz@dlsl.edu.ph"));
        assertThat(withStudentAdmin.resolve("juan_delacruz@dlsl.edu.ph")).isEqualTo(UserRole.ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "juan_delacruz@gmail.com",
            "juan_delacruz@student.dlsl.edu.ph",
            "juan_delacruz@dlsl.edu.ph.evil.com",
            "juan_delacruz@evildlsl.edu.ph",
            "ana.cruz@dlsl.edu.ph@gmail.com",
            "@dlsl.edu.ph",
            "",
    })
    void nonSchoolEmailsAreRejected(String email) {
        assertThatThrownBy(() -> resolver.resolve(email))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("@dlsl.edu.ph");
    }

    @ParameterizedTest
    @ValueSource(strings = {"juan_dela.cruz@dlsl.edu.ph", "a.b_c@dlsl.edu.ph"})
    void emailsWithBothDotAndUnderscoreAreRejected(String email) {
        assertThatThrownBy(() -> resolver.resolve(email))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("not recognized");
    }

    @Test
    void nullEmailIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(LoginRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana.cruz@gmail.com", "sdfo@student.dlsl.edu.ph", "not-an-email"})
    void allowlistRejectsEntriesOutsideTheSchoolDomain(String entry) {
        assertThatThrownBy(() -> new EmailRoleResolver(List.of(entry)))
                .isInstanceOf(IllegalStateException.class);
    }
}
