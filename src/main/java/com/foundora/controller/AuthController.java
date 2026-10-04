package com.foundora.controller;

import com.foundora.auth.EmailRoleResolver;
import com.foundora.auth.FirebaseLoginService;
import com.foundora.auth.FoundoraUser;
import com.foundora.auth.LoginRejectedException;
import com.foundora.config.FirebaseWebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class AuthController {

    private final FirebaseLoginService loginService;
    private final FirebaseWebProperties firebaseWeb;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(FirebaseLoginService loginService, FirebaseWebProperties firebaseWeb) {
        this.loginService = loginService;
        this.firebaseWeb = firebaseWeb;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal FoundoraUser user, Model model) {
        if (user != null) {
            return "redirect:" + user.homePath();
        }
        model.addAttribute("firebase", firebaseWeb);
        model.addAttribute("allowedDomain", EmailRoleResolver.ALLOWED_DOMAIN);
        return "login";
    }

    /** Exchanges a freshly issued Firebase ID token for a server-side session. */
    @PostMapping("/auth/session")
    @ResponseBody
    public ResponseEntity<Map<String, String>> createSession(@RequestBody SessionRequest body,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        FoundoraUser user;
        try {
            user = loginService.authenticate(body.idToken());
        } catch (LoginRejectedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }

        // Start a fresh session so a pre-login session id (and its CSRF token) can't be reused.
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();
        }
        request.getSession(true);

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of(new SimpleGrantedAuthority(user.role().authority())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(Map.of("redirect", user.homePath()));
    }

    public record SessionRequest(String idToken) {
    }
}
