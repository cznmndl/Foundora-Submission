package com.foundora.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailRoleResolverTest {

    private final EmailRoleResolver resolver = new EmailRoleResolver(List.of(" Ana.Cruz@DLSL.edu.ph "));

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
    void dotEmailNotOnAllowlistIsRejected() {
        assertThatThrownBy(() -> resolver.resolve("ben.santos@dlsl.edu.ph"))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("allowlist");
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
    @ValueSource(strings = {"juandelacruz@dlsl.edu.ph", "juan_dela.cruz@dlsl.edu.ph"})
    void ambiguousSchoolEmailsAreRejected(String email) {
        assertThatThrownBy(() -> resolver.resolve(email))
                .isInstanceOf(LoginRejectedException.class)
                .hasMessageContaining("not recognized");
    }

    @Test
    void nullEmailIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(LoginRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"juan_delacruz@dlsl.edu.ph", "ana.cruz@gmail.com"})
    void allowlistRejectsEntriesThatCanNeverBeAdmins(String entry) {
        assertThatThrownBy(() -> new EmailRoleResolver(List.of(entry)))
                .isInstanceOf(IllegalStateException.class);
    }
}
