package com.foundora.config;

import com.foundora.auth.FoundoraUser;
import com.foundora.auth.UserRole;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.foundora.support.TestData.as;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthTest extends WebTestBase {

    @Test
    void answersOkWithoutSignIn() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("OK"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void isNotSentToProfileSetup() throws Exception {
        FoundoraUser noProfile = new FoundoraUser("uid-new", "new_person@dlsl.edu.ph", "New Person", UserRole.STUDENT);
        mvc.perform(get("/health").with(as(noProfile))).andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    void otherPagesStillNeedSignIn() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/healthz")).andExpect(status().is3xxRedirection());
    }
}
