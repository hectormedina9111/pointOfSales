package com.medinasystems.pos.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link CentralOnlyInterceptor} for every API route. */
@Configuration(proxyBeanMethods = false)
public class CentralOnlyWebConfig implements WebMvcConfigurer {

    private final CentralOnlyInterceptor centralOnlyInterceptor;

    public CentralOnlyWebConfig(CentralOnlyInterceptor centralOnlyInterceptor) {
        this.centralOnlyInterceptor = centralOnlyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(centralOnlyInterceptor).addPathPatterns("/api/v1/**");
    }
}
