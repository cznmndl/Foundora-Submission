package com.foundora.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ProfileSetupInterceptor implements HandlerInterceptor {

    public static final String SETUP_PATH = "/profile/setup";
    public static final String COMPLETE_ATTRIBUTE = "foundora.profileComplete";

    private final ProfileService profiles;

    public ProfileSetupInterceptor(ProfileService profiles) {
        this.profiles = profiles;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FoundoraUser user)) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(COMPLETE_ATTRIBUTE))) {
            return true;
        }
        if (profiles.isComplete(user)) {
            if (session != null) {
                session.setAttribute(COMPLETE_ATTRIBUTE, true);
            }
            return true;
        }
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", request.getContextPath() + SETUP_PATH);
        return false;
    }
}
