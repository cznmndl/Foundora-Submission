package com.foundora.auth;

import com.foundora.config.Formats;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PinController {

    private final PinService pins;
    private final ProfileService profiles;
    private final Formats fmt;

    public PinController(PinService pins, ProfileService profiles, Formats fmt) {
        this.pins = pins;
        this.profiles = profiles;
        this.fmt = fmt;
    }

    @GetMapping(PinInterceptor.SETUP_PATH)
    public String setupForm(@AuthenticationPrincipal FoundoraUser user, HttpSession session) {
        if (PinInterceptor.isVerified(session)) {
            return "redirect:" + user.homePath();
        }
        if (pins.state(user).status() != PinService.Status.NEEDS_SETUP) {
            return "redirect:" + PinInterceptor.PIN_PATH;
        }
        return "pin-setup";
    }

    @PostMapping(PinInterceptor.SETUP_PATH)
    public String setup(@AuthenticationPrincipal FoundoraUser user, @RequestParam(defaultValue = "") String pin,
                        @RequestParam(defaultValue = "") String confirm, HttpServletRequest request, Model model) {
        if (PinInterceptor.isVerified(request.getSession(false))) {
            return "redirect:" + user.homePath();
        }
        String problem = PinService.problem(pin, confirm);
        if (problem != null) {
            model.addAttribute("pinError", problem);
            return "pin-setup";
        }
        if (!pins.create(user, pin, confirm)) {
            return "redirect:" + PinInterceptor.PIN_PATH;
        }
        return verified(user, request);
    }

    @GetMapping(PinInterceptor.PIN_PATH)
    public String entryForm(@AuthenticationPrincipal FoundoraUser user, HttpSession session, Model model) {
        if (PinInterceptor.isVerified(session)) {
            return "redirect:" + user.homePath();
        }
        PinService.State state = pins.state(user);
        if (state.status() == PinService.Status.NEEDS_SETUP) {
            return "redirect:" + PinInterceptor.SETUP_PATH;
        }
        boolean locked = state.status() == PinService.Status.LOCKED;
        model.addAttribute("locked", locked);
        if (locked) {
            model.addAttribute("lockedUntil", fmt.dateTime(state.lockedUntil()));
        }
        return "pin";
    }

    @PostMapping(PinInterceptor.PIN_PATH)
    public String enter(@AuthenticationPrincipal FoundoraUser user, @RequestParam(defaultValue = "") String pin,
                        HttpServletRequest request, RedirectAttributes redirect) {
        if (PinInterceptor.isVerified(request.getSession(false))) {
            return "redirect:" + user.homePath();
        }
        PinService.Check check = pins.verify(user, pin);
        if (check instanceof PinService.Check.Correct) {
            return verified(user, request);
        }
        if (check instanceof PinService.Check.NoPin) {
            return "redirect:" + PinInterceptor.SETUP_PATH;
        }
        if (check instanceof PinService.Check.Wrong wrong) {
            redirect.addFlashAttribute("pinError", "Incorrect. " + wrong.attemptsLeft()
                    + (wrong.attemptsLeft() == 1 ? " attempt" : " attempts") + " left.");
        }
        return "redirect:" + PinInterceptor.PIN_PATH;
    }

    private String verified(FoundoraUser user, HttpServletRequest request) {
        request.changeSessionId();
        request.getSession().setAttribute(PinInterceptor.VERIFIED_ATTRIBUTE, true);
        return "redirect:" + (profiles.isComplete(user) ? user.homePath() : ProfileSetupInterceptor.SETUP_PATH);
    }
}
