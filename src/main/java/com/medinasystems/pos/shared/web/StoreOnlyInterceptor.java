package com.medinasystems.pos.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.StoreOnly;
import com.medinasystems.pos.shared.StoreOnlyOperationException;

/**
 * Rejects calls to {@link StoreOnly} handlers when this node is the central node. The exception is rendered by
 * {@link GlobalExceptionHandler}.
 */
@Component
public class StoreOnlyInterceptor implements HandlerInterceptor {

    private final NodeProperties node;

    public StoreOnlyInterceptor(NodeProperties node) {
        this.node = node;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (node.isCentral() && handler instanceof HandlerMethod method && isStoreOnly(method)) {
            throw new StoreOnlyOperationException();
        }
        return true;
    }

    static boolean isStoreOnly(HandlerMethod method) {
        return method.hasMethodAnnotation(StoreOnly.class)
                || method.getBeanType().isAnnotationPresent(StoreOnly.class);
    }
}
