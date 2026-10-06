package com.foundora.controller;

import com.foundora.model.ActivityAction;
import com.foundora.model.Comment;
import com.foundora.model.Item;
import com.foundora.model.ReportStatus;
import com.foundora.model.ReportType;
import com.foundora.service.StorageService;
import com.foundora.support.WebTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static com.foundora.support.TestData.ADMIN;
import static com.foundora.support.TestData.OTHER_STUDENT;
import static com.foundora.support.TestData.STAFF;
import static com.foundora.support.TestData.STUDENT;
import static com.foundora.support.TestData.as;
import static com.foundora.support.TestData.item;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentTest extends WebTestBase {

    @Test
    void commentsAppearAtOnceWithNameBadgeAndTime() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);

        comment(post, "I think this is mine!", as(STUDENT))
                .andExpect(redirectedUrl("/posts/" + post.getId() + "#comments"))
                .andExpect(flash().attribute("flashSuccess", "Your comment was posted."));
        comment(post, "Please claim it at SDFO.", as(STAFF));

        String html = html("/posts/" + post.getId(), as(OTHER_STUDENT));
        assertThat(html).contains("I think this is mine!").contains("Juan Dela Cruz").contains(">Student<")
                .contains("Ben Reyes").contains(">Staff<").contains("just now").contains("2 comments");
        assertThat(html.indexOf("I think this is mine!")).isLessThan(html.indexOf("Please claim it at SDFO."));
        assertThat(html("/", as(STUDENT))).contains("2 comments");
    }

    @Test
    void onlyAdminsSeeCommentersEmailsAndProfiles() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);
        comment(post, "Mine, it has my name inside", as(STUDENT));

        assertThat(html("/posts/" + post.getId(), as(STAFF))).contains("Juan Dela Cruz")
                .doesNotContain(STUDENT.email()).doesNotContain("BS Information Technology");
        assertThat(html("/posts/" + post.getId(), as(ADMIN))).contains(STUDENT.email())
                .contains("BS Information Technology · 2nd year · O1A");
    }

    @Test
    void theReportersCommentsAreMarkedAsTheOriginalPoster() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);
        comment(post, "Still looking for the owner", as(OTHER_STUDENT));

        assertThat(html("/posts/" + post.getId(), as(STUDENT))).contains("Original poster");
    }

    @Test
    void refusesEmptyTooLongAndTooFastComments() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);

        comment(post, "   ", as(STUDENT)).andExpect(flash().attribute("flashError", "Write a comment or add a photo."));
        comment(post, "x".repeat(1001), as(STUDENT))
                .andExpect(flash().attribute("flashError", containsString("1000 characters")));
        comment(post, "First", as(STUDENT)).andExpect(flash().attribute("flashSuccess", "Your comment was posted."));
        comment(post, "Second, straight away", as(STUDENT))
                .andExpect(flash().attribute("flashError", containsString("too fast")))
                .andExpect(flash().attribute("commentDraft", "Second, straight away"));

        assertThat(commentRepository.count()).isEqualTo(1);
    }

    @Test
    void commentsAreClosedOnPostsThatAreNotOnTheFeed() throws Exception {
        Item pending = save(item("Pending wallet", ReportType.LOST, ReportStatus.PENDING, STUDENT));
        Item archived = item("Archived keys", ReportType.FOUND, ReportStatus.CLAIMED, OTHER_STUDENT);
        archived.setClaimedAt(Instant.now());
        archived.setArchivedAt(Instant.now());
        archived = save(archived);

        comment(pending, "Hello", as(STUDENT)).andExpect(flash().attribute("flashError", "Comments are closed on this post."));
        comment(archived, "Hello", as(ADMIN)).andExpect(flash().attribute("flashError", "Comments are closed on this post."));
        assertThat(html("/posts/" + archived.getId(), as(ADMIN))).contains("Comments are closed on this post.")
                .doesNotContain("Post comment");
        assertThat(commentRepository.count()).isZero();
    }

    @Test
    void claimedPostsStillTakeComments() throws Exception {
        Item claimed = item("Claimed tumbler", ReportType.FOUND, ReportStatus.CLAIMED, OTHER_STUDENT);
        claimed.setClaimedAt(Instant.now());
        claimed = save(claimed);

        comment(claimed, "Thank you SDFO!", as(STUDENT)).andExpect(flash().attribute("flashSuccess", "Your comment was posted."));
    }

    @Test
    void usersDeleteTheirOwnCommentsAndAdminsDeleteAnyWithALogEntry() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);
        comment(post, "Mine", as(STUDENT));
        Comment mine = commentRepository.findAll().get(0);

        mvc.perform(post("/comments/" + mine.getId() + "/delete").with(as(STAFF)).with(csrf()))
                .andExpect(flash().attribute("flashError", "You can only delete your own comments."));
        assertThat(commentRepository.count()).isEqualTo(1);

        mvc.perform(post("/comments/" + mine.getId() + "/delete").with(as(STUDENT)).with(csrf()))
                .andExpect(redirectedUrl("/posts/" + post.getId() + "#comments"));
        assertThat(commentRepository.count()).isZero();
        assertThat(activityLog.findAll()).isEmpty(); // a user deleting their own comment isn't logged

        comment(post, "Spam spam", as(STAFF));
        Comment spam = commentRepository.findAll().get(0);
        mvc.perform(post("/comments/" + spam.getId() + "/delete").param("reason", "Spam")
                        .with(as(ADMIN)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", "Comment deleted."));
        assertThat(commentRepository.count()).isZero();
        assertThat(activityLog.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(ActivityAction.COMMENT_DELETED);
            assertThat(e.getActorEmail()).isEqualTo(ADMIN.email());
            assertThat(e.getReason()).isEqualTo("Comment by Ben Reyes. Reason: Spam");
            assertThat(e.getItemId()).isEqualTo(post.getId());
        });
    }

    @Test
    void commentPhotosAreReEncodedStoredPrivatelyAndDeletedWithTheComment() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);
        when(storage.storePhoto(eq(StorageService.COMMENTS), any())).thenReturn("comments/abc.jpg");
        when(storage.downloadPhoto("comments/abc.jpg"))
                .thenReturn(new StorageService.StoredPhoto(new byte[]{9}, MediaType.IMAGE_JPEG));

        mvc.perform(multipart("/posts/" + post.getId() + "/comments")
                        .file(new MockMultipartFile("photo", "id.png", "image/png", ReportSubmissionTest.png(300, 200)))
                        .with(as(STUDENT)).with(csrf()))
                .andExpect(flash().attribute("flashSuccess", "Your comment was posted."));

        ArgumentCaptor<byte[]> stored = ArgumentCaptor.forClass(byte[].class);
        verify(storage).storePhoto(eq(StorageService.COMMENTS), stored.capture());
        assertThat(stored.getValue()).startsWith((byte) 0xFF, (byte) 0xD8); // JPEG: metadata stripped
        Comment withPhoto = commentRepository.findAll().get(0);
        assertThat(withPhoto.getImagePath()).isEqualTo("comments/abc.jpg");
        assertThat(withPhoto.getBody()).isNull();

        mvc.perform(get("/comments/" + withPhoto.getId() + "/photo").with(as(STAFF)))
                .andExpect(status().isOk());
        mvc.perform(get("/comments/" + withPhoto.getId() + "/photo")).andExpect(status().is3xxRedirection());

        mvc.perform(post("/comments/" + withPhoto.getId() + "/delete").with(as(STUDENT)).with(csrf()));
        verify(storage).deletePhotoQuietly("comments/abc.jpg");
    }

    @Test
    void commentPhotosFollowThePostsAccessRules() throws Exception {
        Item post = approvedPostBy(OTHER_STUDENT);
        when(storage.storePhoto(eq(StorageService.COMMENTS), any())).thenReturn("comments/def.jpg");
        mvc.perform(multipart("/posts/" + post.getId() + "/comments")
                .file(new MockMultipartFile("photo", "x.png", "image/png", ReportSubmissionTest.png(20, 20)))
                .with(as(STUDENT)).with(csrf()));
        Comment comment = commentRepository.findAll().get(0);

        post.setStatus(ReportStatus.REMOVED);
        save(post);

        mvc.perform(get("/comments/" + comment.getId() + "/photo").with(as(STUDENT))).andExpect(status().isNotFound());
    }

    private Item approvedPostBy(com.foundora.auth.FoundoraUser reporter) {
        return save(item("Navy umbrella", ReportType.FOUND, ReportStatus.APPROVED, reporter));
    }

    private org.springframework.test.web.servlet.ResultActions comment(Item post, String body, RequestPostProcessor user)
            throws Exception {
        return mvc.perform(multipart("/posts/" + post.getId() + "/comments").param("body", body)
                .with(user).with(csrf()));
    }

    private String html(String url, RequestPostProcessor user) throws Exception {
        return mvc.perform(get(url).with(user)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
