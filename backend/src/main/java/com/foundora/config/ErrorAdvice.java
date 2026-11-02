package com.foundora.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.regex.Pattern;

@ControllerAdvice
public class ErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(ErrorAdvice.class);

    private static final Pattern COMMENT_POST = Pattern.compile("/posts/[0-9a-fA-F-]{36}/comments");

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("flashError", "The photo must be 5 MB or smaller. Please choose a smaller photo.");
        String path = request.getRequestURI();
        if (COMMENT_POST.matcher(path).matches()) {
            return "redirect:" + path.substring(0, path.length() - "/comments".length()) + "#comments";
        }
        return "redirect:/report";
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class, TransientDataAccessResourceException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String databaseUnavailable(Exception e, HttpServletRequest request) {
        log.warn("Database unreachable while serving {}", request.getRequestURI(), e);
        return "error/503";
    }
}
