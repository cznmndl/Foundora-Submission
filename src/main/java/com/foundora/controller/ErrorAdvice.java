package com.foundora.controller;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class ErrorAdvice {

    /** An oversized upload is rejected before the controller runs; send the user back to the form with a message. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "The photo must be 5 MB or smaller. Please choose a smaller photo.");
        return "redirect:/report";
    }
}
