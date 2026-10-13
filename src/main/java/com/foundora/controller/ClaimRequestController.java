package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import com.foundora.model.ClaimRequest;
import com.foundora.service.ClaimRequestException;
import com.foundora.service.ClaimRequestService;
import com.foundora.service.InvalidImageException;
import com.foundora.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

/** "This is mine": the requester's side of a claim request. Admin decisions are in {@link AdminController}. */
@Controller
public class ClaimRequestController {

    private static final Logger log = LoggerFactory.getLogger(ClaimRequestController.class);

    private final ClaimRequestService claimRequests;
    private final StorageService storage;

    public ClaimRequestController(ClaimRequestService claimRequests, StorageService storage) {
        this.claimRequests = claimRequests;
        this.storage = storage;
    }

    @PostMapping("/posts/{id}/claim-requests")
    public String request(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user,
                          @RequestParam(required = false) String proof,
                          @RequestParam(value = "photo", required = false) MultipartFile photo,
                          RedirectAttributes redirect) {
        try {
            claimRequests.request(id, user, proof, photo);
            redirect.addFlashAttribute("flashSuccess",
                    "Request sent. SDFO will review it and you'll see the result on this page and in My Reports.");
        } catch (ClaimRequestException | InvalidImageException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            redirect.addFlashAttribute("proofDraft", proof);
        }
        return "redirect:/posts/" + id + "#claim";
    }

    @PostMapping("/claim-requests/{id}/reply")
    public String reply(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user,
                        @RequestParam(required = false) String reply, RedirectAttributes redirect) {
        return act(() -> claimRequests.reply(id, user, reply), "Answer sent to SDFO.", redirect);
    }

    @PostMapping("/claim-requests/{id}/withdraw")
    public String withdraw(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user,
                           RedirectAttributes redirect) {
        return act(() -> claimRequests.withdraw(id, user), "Request withdrawn.", redirect);
    }

    /** The request's photo: the requester and admins only. */
    @GetMapping("/claim-requests/{id}/photo")
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @AuthenticationPrincipal FoundoraUser user) {
        ClaimRequest request = claimRequests.findPhotoViewable(id, user).orElse(null);
        if (request == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            StorageService.StoredPhoto photo = storage.downloadPhoto(request.getImagePath());
            return ResponseEntity.ok()
                    .contentType(photo.contentType())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .eTag(request.getImagePath())
                    .body(photo.bytes());
        } catch (RestClientException e) {
            log.warn("Could not load photo {} for claim request {}", request.getImagePath(), id, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private String act(java.util.function.Supplier<ClaimRequest> action, String done, RedirectAttributes redirect) {
        ClaimRequest request;
        try {
            request = action.get();
            redirect.addFlashAttribute("flashSuccess", done);
        } catch (ClaimRequestException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:/my-reports#claims";
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("flashError", "SDFO changed this request at the same moment. Check it and try again.");
            return "redirect:/my-reports#claims";
        }
        return "redirect:/posts/" + request.getItemId() + "#claim";
    }
}
