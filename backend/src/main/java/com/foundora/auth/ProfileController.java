package com.foundora.auth;

import com.foundora.database.entity.UserProfile;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final ProfileService profiles;
    private final ProfileOptions options;

    public ProfileController(ProfileService profiles, ProfileOptions options) {
        this.profiles = profiles;
        this.options = options;
    }

    @GetMapping({"/profile", ProfileSetupInterceptor.SETUP_PATH})
    public String form(@AuthenticationPrincipal FoundoraUser user, Model model) {
        UserProfile profile = profiles.find(user).orElse(null);
        if (!model.containsAttribute("profileForm")) {
            model.addAttribute("profileForm", ProfileForm.from(profile, options));
        }
        return page(user, profile, model);
    }

    @PostMapping({"/profile", ProfileSetupInterceptor.SETUP_PATH})
    public String save(@AuthenticationPrincipal FoundoraUser user,
                       @ModelAttribute("profileForm") ProfileForm form, BindingResult errors,
                       HttpSession session, Model model, RedirectAttributes redirect) {
        boolean wasComplete = profiles.isComplete(user);
        form.validate(user.role(), options, errors);
        if (errors.hasErrors()) {
            return page(user, profiles.find(user).orElse(null), model);
        }
        profiles.save(user, form);
        session.setAttribute(ProfileSetupInterceptor.COMPLETE_ATTRIBUTE, true);
        if (!wasComplete) {
            redirect.addFlashAttribute("flashSuccess", "Thanks! Your profile is set up.");
            return "redirect:" + user.homePath();
        }
        redirect.addFlashAttribute("flashSuccess", "Your profile was updated.");
        return "redirect:/profile";
    }

    private String page(FoundoraUser user, UserProfile profile, Model model) {
        boolean setup = profile == null || !profile.isCompleteFor(user.role());
        model.addAttribute("setupMode", setup);
        model.addAttribute("isStudent", user.role() == com.foundora.auth.UserRole.STUDENT);
        model.addAttribute("nav", "profile");
        return "profile";
    }
}
