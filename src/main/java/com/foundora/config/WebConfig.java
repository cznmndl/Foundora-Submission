package com.foundora.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ProfileSetupInterceptor profileSetup;

    public WebConfig(ProfileSetupInterceptor profileSetup) {
        this.profileSetup = profileSetup;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(profileSetup)
                .addPathPatterns("/**")
                .excludePathPatterns("/profile", "/profile/setup", "/login", "/auth/**", "/error", "/error/**",
                        "/css/**", "/js/**", "/img/**", "/favicon.svg", "/health");
    }
}
