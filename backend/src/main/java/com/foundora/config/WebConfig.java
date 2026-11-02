package com.foundora.config;

import com.foundora.auth.PinInterceptor;
import com.foundora.auth.ProfileSetupInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] PUBLIC = {"/login", "/auth/**", "/error", "/error/**", "/css/**", "/js/**",
            "/fonts/**", "/img/**", "/favicon.svg", "/health"};

    private final PinInterceptor pin;
    private final ProfileSetupInterceptor profileSetup;

    public WebConfig(PinInterceptor pin, ProfileSetupInterceptor profileSetup) {
        this.pin = pin;
        this.profileSetup = profileSetup;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pin)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC)
                .excludePathPatterns("/pin", "/pin/**");
        registry.addInterceptor(profileSetup)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC)
                .excludePathPatterns("/pin", "/pin/**", "/profile", "/profile/setup");
    }
}
