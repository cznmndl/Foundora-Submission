package com.foundora.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorAdviceTest {

    private final ErrorAdvice advice = new ErrorAdvice();

    @Test
    void oversizedUploadsGoBackToTheReportFormWithAMessage() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        assertThat(advice.uploadTooLarge(new MockHttpServletRequest("POST", "/report"), redirect))
                .isEqualTo("redirect:/report");
        assertThat(redirect.getFlashAttributes().get("flashError")).asString().contains("5 MB or smaller");
    }

    @Test
    void oversizedCommentPhotosGoBackToThePostsComments() {
        String id = UUID.randomUUID().toString();

        assertThat(advice.uploadTooLarge(new MockHttpServletRequest("POST", "/posts/" + id + "/comments"),
                new RedirectAttributesModelMap())).isEqualTo("redirect:/posts/" + id + "#comments");
    }
}
