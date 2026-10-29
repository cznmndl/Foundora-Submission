package com.foundora.moderation;

import com.foundora.auth.FoundoraUser;
import com.foundora.config.Params;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.function.Supplier;

/** The admin's Accounts page: find a person, reset a forgotten PIN, or unlock an account locked by wrong PINs. */
@Controller
@RequestMapping("/admin/accounts")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public String accounts(@RequestParam(required = false) String q, Model model) {
        String text = Params.text(q, 100);
        model.addAttribute("q", text);
        model.addAttribute("accounts", accounts.search(text));
        model.addAttribute("nav", "admin");
        model.addAttribute("adminNav", "accounts");
        return "admin/accounts";
    }

    @PostMapping("/reset-pin")
    public String resetPin(@AuthenticationPrincipal FoundoraUser admin, @RequestParam String uid,
                           @RequestParam(required = false) String q, RedirectAttributes redirect) {
        return act(() -> "Reset the PIN for " + accounts.resetPin(admin, uid).getFullName()
                + ". They'll create a new PIN on their next sign-in.", q, redirect);
    }

    @PostMapping("/unlock")
    public String unlock(@AuthenticationPrincipal FoundoraUser admin, @RequestParam String uid,
                         @RequestParam(required = false) String q, RedirectAttributes redirect) {
        return act(() -> "Unlocked " + accounts.unlock(admin, uid).getFullName() + "'s account.", q, redirect);
    }

    private String act(Supplier<String> action, String q, RedirectAttributes redirect) {
        try {
            redirect.addFlashAttribute("flashSuccess", action.get());
        } catch (ModerationException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("flashError",
                    "Someone else changed this account at the same moment. Check it and try again.");
        }
        String text = Params.text(q, 100);
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/accounts")
                .queryParamIfPresent("q", Optional.ofNullable(text)).build().encode().toUriString();
    }
}
