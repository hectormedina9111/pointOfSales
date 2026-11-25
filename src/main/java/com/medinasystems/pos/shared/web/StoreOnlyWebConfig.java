package com.medinasystems.pos.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers {@link StoreOnlyInterceptor} for every API route. */
@Configuration(proxyBeanMethods = false)
public class StoreOnlyWebConfig implements WebMvcConfigurer {

    private final StoreOnlyInterceptor storeOnlyInterceptor;

    public StoreOnlyWebConfig(StoreOnlyInterceptor storeOnlyInterceptor) {
        this.storeOnlyInterceptor = storeOnlyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(storeOnlyInterceptor).addPathPatterns("/api/v1/**");
    }
}
