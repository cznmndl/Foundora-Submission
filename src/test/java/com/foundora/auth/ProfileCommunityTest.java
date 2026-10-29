package com.foundora.auth;

import com.foundora.database.entity.Community;
import com.foundora.database.entity.UserProfile;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileCommunityTest extends WebTestBase {

    @Test
    void theCommunityIsRequired() throws Exception {
        mvc.perform(save().param("section", "Rizal")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Choose your community.")));
    }

    @Test
    void basicEducationAsksForAGradeInItsRangeAndClearsCollegeDetails() throws Exception {
        // Grade 11 isn't Junior High.
        mvc.perform(save().param("community", "JUNIOR_HIGH").param("gradeLevel", "11").param("section", "Rizal"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Junior High School is Grades 7–10")));

        // College fields sent along (no JavaScript) are ignored and cleared.
        mvc.perform(save().param("community", "JUNIOR_HIGH").param("gradeLevel", "8").param("section", "Rizal")
                        .param("course", "BS Psychology").param("yearLevel", "2"))
                .andExpect(redirectedUrl("/profile"));
        UserProfile p = profile();
        assertThat(p.getCommunity()).isEqualTo(Community.JUNIOR_HIGH);
        assertThat(p.getGradeLevel()).isEqualTo(8);
        assertThat(p.getSection()).isEqualTo("Rizal");          // names keep their case
        assertThat(p.getCourse()).isNull();
        assertThat(p.getYearLevel()).isNull();
        assertThat(p.summary()).isEqualTo("JHS · Grade 8 · Section Rizal");
        assertThat(p.isCompleteFor(UserRole.STUDENT)).isTrue();
    }

    @Test
    void eachBasicEducationCommunityHasItsOwnGrades() throws Exception {
        mvc.perform(save().param("community", "ELEMENTARY").param("gradeLevel", "7").param("section", "Sampaguita"))
                .andExpect(content().string(containsString("Elementary is Grades 1–6")));
        mvc.perform(save().param("community", "SENIOR_HIGH").param("gradeLevel", "10").param("section", "STEM A"))
                .andExpect(content().string(containsString("Senior High School is Grades 11–12")));
        mvc.perform(save().param("community", "SENIOR_HIGH").param("gradeLevel", "12").param("section", "STEM A"))
                .andExpect(redirectedUrl("/profile"));
        assertThat(profile().summary()).isEqualTo("SHS · Grade 12 · Section STEM A");
    }

    @Test
    void collegeNeedsAListedCourseAndYearAndClearsTheGrade() throws Exception {
        mvc.perform(save().param("community", "COLLEGE").param("course", "BS Marine Transportation")
                        .param("yearLevel", "2").param("section", "o2a"))
                .andExpect(content().string(containsString("Choose a course from the list.")));
        mvc.perform(save().param("community", "COLLEGE").param("section", "O2A"))
                .andExpect(content().string(containsString("Choose your course.")))
                .andExpect(content().string(containsString("Choose your year level.")));

        mvc.perform(save().param("community", "COLLEGE").param("course", "BS Computer Engineering")
                        .param("yearLevel", "2").param("section", "o2a").param("gradeLevel", "8"))
                .andExpect(redirectedUrl("/profile"));
        UserProfile p = profile();
        assertThat(p.getCommunity()).isEqualTo(Community.COLLEGE);
        assertThat(p.getGradeLevel()).isNull();
        assertThat(p.summary()).isEqualTo("BS Computer Engineering · 2nd year · O2A");
    }

    @Test
    void lawAndNursingAreSelectableForNow() throws Exception {
        mvc.perform(save().param("community", "COLLEGE").param("course", "College of Nursing")
                        .param("yearLevel", "1").param("section", "N1A"))
                .andExpect(redirectedUrl("/profile"));
        assertThat(profile().getCourse()).isEqualTo("College of Nursing");
    }

    @Test
    void existingProfilesWithACourseCountAsCollegeAndStayComplete() throws Exception {
        UserProfile old = profile();           // saved by the test setup with a course and no community
        assertThat(old.getCommunity()).isNull();
        assertThat(old.effectiveCommunity()).isEqualTo(Community.COLLEGE);
        assertThat(old.isCompleteFor(UserRole.STUDENT)).isTrue();
        mvc.perform(get("/").with(as(STUDENT))).andExpect(status().isOk());   // no redirect to setup
        assertThat(mvc.perform(get("/profile").with(as(STUDENT))).andReturn().getResponse().getContentAsString())
                .containsPattern("value=\"COLLEGE\"\\s+required checked=\"checked\"");
    }

    @Test
    void anUnlistedSavedCourseIsShownNotLost() throws Exception {
        UserProfile p = profile();
        p.setCourse("BS Civil Engineering");
        profileRepository.save(p);
        mvc.perform(get("/profile").with(as(STUDENT)))
                .andExpect(content().string(containsString("Your saved course, “BS Civil Engineering”")));
    }

    @Test
    void staffAndAdminsAreNeverAskedForACommunity() throws Exception {
        mvc.perform(get("/profile").with(as(STAFF)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("name=\"community\""))));
        mvc.perform(post("/profile").param("department", "Library").param("community", "COLLEGE")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(redirectedUrl("/profile"));
        UserProfile admin = profileRepository.findById(ADMIN.uid()).orElseThrow();
        assertThat(admin.getCommunity()).isNull();
        assertThat(admin.summary()).isEqualTo("Library");
    }

    @Test
    void basicEducationDetailsShowUpForAdminsOnPosts() throws Exception {
        UserProfile p = profile();
        p.setCommunity(Community.ELEMENTARY);
        p.setCourse(null);
        p.setYearLevel(null);
        p.setGradeLevel(5);
        p.setSection("Sampaguita");
        p.setUpdatedAt(Instant.now());
        profileRepository.save(p);
        var item = save(com.foundora.support.TestData.item("Pencil case", com.foundora.database.entity.ReportType.LOST,
                com.foundora.database.entity.ReportStatus.APPROVED, STUDENT));
        mvc.perform(get("/posts/" + item.getId()).with(as(ADMIN)))
                .andExpect(content().string(containsString("Elementary · Grade 5 · Section Sampaguita")));
    }

    private MockHttpServletRequestBuilder save() {
        return post("/profile").with(as(STUDENT)).with(csrf());
    }

    private UserProfile profile() {
        return profileRepository.findById(STUDENT.uid()).orElseThrow();
    }
}
