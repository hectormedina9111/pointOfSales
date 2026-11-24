package com.medinasystems.pos.security.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link PrivilegeInterceptor} for every API route. */
@Configuration(proxyBeanMethods = false)
public class PrivilegeWebConfig implements WebMvcConfigurer {

    private final PrivilegeInterceptor privilegeInterceptor;

    public PrivilegeWebConfig(PrivilegeInterceptor privilegeInterceptor) {
        this.privilegeInterceptor = privilegeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(privilegeInterceptor).addPathPatterns("/api/v1/**");
    }
}
