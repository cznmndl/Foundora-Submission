package com.foundora.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Until the PIN has been entered in this session, every page (except the PIN pages, sign-in and static files,
 * see {@link WebConfig}) sends a signed-in user to /pin. It runs before {@link ProfileSetupInterceptor}.
 */
@Component
public class PinInterceptor implements HandlerInterceptor {

    public static final String PIN_PATH = "/pin";
    public static final String SETUP_PATH = "/pin/setup";
    public static final String VERIFIED_ATTRIBUTE = "foundora.pinVerified";

    public static boolean isVerified(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED_ATTRIBUTE));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof FoundoraUser)) {
            return true;
        }
        if (isVerified(request.getSession(false))) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_SEE_OTHER);
        response.setHeader("Location", request.getContextPath() + PIN_PATH);
        return false;
    }
}
