package com.foundora.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

@ControllerAdvice
public class ErrorAdvice {

    private static final Pattern COMMENT_POST = Pattern.compile("/posts/[0-9a-fA-F-]{36}/comments");

    /**
     * An oversized upload is rejected before the controller runs; send the user back to the form they used
     * (a post's comment box, or the report form) with a message.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "The photo must be 5 MB or smaller. Please choose a smaller photo.");
        String path = request.getRequestURI();
        if (COMMENT_POST.matcher(path).matches()) {
            return "redirect:" + path.substring(0, path.length() - "/comments".length()) + "#comments";
        }
        return "redirect:/report";
    }
}
