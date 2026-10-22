package com.foundora.feed;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.Params;
import com.foundora.database.entity.Comment;
import com.foundora.database.entity.Item;
import com.foundora.database.storage.StorageService;
import com.foundora.reporting.InvalidImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.UUID;

/** Posting and deleting comments, and serving comment photos to people who may see the post. */
@Controller
public class CommentController {

    private static final Logger log = LoggerFactory.getLogger(CommentController.class);

    private final CommentService comments;
    private final StorageService storage;

    public CommentController(CommentService comments, StorageService storage) {
        this.comments = comments;
        this.storage = storage;
    }

    @PostMapping("/posts/{id}/comments")
    public String add(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user,
                      @RequestParam(required = false) String body,
                      @RequestParam(value = "photo", required = false) MultipartFile photo,
                      RedirectAttributes redirect) {
        try {
            comments.add(id, user, body, photo);
            redirect.addFlashAttribute("flashSuccess", "Your comment was posted.");
        } catch (CommentException | InvalidImageException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            redirect.addFlashAttribute("commentDraft", body);
        }
        return "redirect:/posts/" + id + "#comments";
    }

    @PostMapping("/comments/{id}/delete")
    public String delete(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user,
                         @RequestParam(required = false) String reason,
                         @RequestParam(required = false) String returnTo, RedirectAttributes redirect) {
        try {
            Item item = comments.delete(id, user, reason);
            redirect.addFlashAttribute("flashSuccess", "Comment deleted.");
            return "redirect:/posts/" + item.getId() + "#comments";
        } catch (CommentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:" + Params.safeReturn(returnTo, "/");
        }
    }

    @GetMapping("/comments/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user) {
        Comment comment = comments.findPhotoViewable(id, user).orElse(null);
        if (comment == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            StorageService.StoredPhoto photo = storage.downloadPhoto(comment.getImagePath());
            return ResponseEntity.ok()
                    .contentType(photo.contentType())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .eTag(comment.getImagePath())
                    .body(photo.bytes());
        } catch (RestClientException e) {
            log.warn("Could not load photo {} for comment {}", comment.getImagePath(), id, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
