package com.foundora.controller;

import com.foundora.model.Community;
import com.foundora.model.UserProfile;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileAndOfficeTest extends WebTestBase {

    @Test
    void myProfileShowsAndUpdatesTheStudentsDetails() throws Exception {
        mvc.perform(get("/profile").with(as(STUDENT))).andExpect(status().isOk())
                .andExpect(content().string(containsString("My Profile")))
                .andExpect(content().string(containsString(STUDENT.email())))
                .andExpect(content().string(containsString("BS Information Technology")))
                .andExpect(content().string(containsString("value=\"O1A\"")))
                .andExpect(content().string(not(containsString("id=\"department\""))));

        mvc.perform(post("/profile").param("community", "COLLEGE").param("course", "BS Computer Science")
                        .param("yearLevel", "3").param("section", "o3a").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/profile"))
                .andExpect(flash().attribute("flashSuccess", "Your profile was updated."));

        UserProfile saved = profileRepository.findById(STUDENT.uid()).orElseThrow();
        assertThat(saved.getCommunity()).isEqualTo(Community.COLLEGE);
        assertThat(saved.getCourse()).isEqualTo("BS Computer Science");
        assertThat(saved.getYearLevel()).isEqualTo(3);
        assertThat(saved.getSection()).isEqualTo("O3A");
        // The edit form shows the saved course selected, inside its college's group.
        mvc.perform(get("/profile").with(as(STUDENT)))
                .andExpect(content().string(containsString("<optgroup label=\"College of Information Technology and Engineering\"")))
                .andExpect(content().string(containsString("value=\"BS Computer Science\" selected=\"selected\"")));
    }

    @Test
    void staffAndAdminProfilesAskForADepartment() throws Exception {
        mvc.perform(get("/profile").with(as(STAFF)))
                .andExpect(content().string(containsString("id=\"department\"")))
                .andExpect(content().string(not(containsString("id=\"course\""))));
        mvc.perform(post("/profile").param("department", "Library").with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/profile"));
        assertThat(profileRepository.findById(ADMIN.uid()).orElseThrow().getDepartment()).isEqualTo("Library");
    }

    @Test
    void invalidYearLevelsAndSectionsAreRefused() throws Exception {
        mvc.perform(post("/profile").param("community", "COLLEGE").param("course", "BS Psychology")
                        .param("yearLevel", "9").param("section", "A;B")
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(content().string(containsString("Choose your year level.")))
                .andExpect(content().string(containsString("letters, numbers and dashes")));
        assertThat(profileRepository.findById(STUDENT.uid()).orElseThrow().getYearLevel()).isEqualTo(2);
    }

    @Test
    void sdfoOfficeDetailsAreShown() throws Exception {
        mvc.perform(get("/").with(as(STUDENT)))
                .andExpect(content().string(containsString("SDFO office")))
                .andExpect(content().string(containsString("Found items are turned in here, and owners pick them up here.")))
                .andExpect(content().string(containsString("MB 3rd Floor")))
                .andExpect(content().string(containsString("8:00 AM – 4:00 PM")))
                .andExpect(content().string(not(containsString("placeholder)"))));
    }
}
