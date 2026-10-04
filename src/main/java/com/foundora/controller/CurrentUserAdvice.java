package com.foundora.controller;

import com.foundora.auth.FoundoraUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the signed-in user available to every template as {@code currentUser}. */
@ControllerAdvice
public class CurrentUserAdvice {

    @ModelAttribute("currentUser")
    public FoundoraUser currentUser(@AuthenticationPrincipal FoundoraUser user) {
        return user;
    }
}
