package com.medinasystems.pos.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.ReadOnlyNodeException;

/**
 * Rejects calls to {@link CentralOnly} handlers when this node is a store. The exception is
 * rendered by {@link GlobalExceptionHandler}.
 */
@Component
public class CentralOnlyInterceptor implements HandlerInterceptor {

    private final NodeProperties node;

    public CentralOnlyInterceptor(NodeProperties node) {
        this.node = node;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (node.isStore() && handler instanceof HandlerMethod method && isCentralOnly(method)) {
            throw new ReadOnlyNodeException();
        }
        return true;
    }

    static boolean isCentralOnly(HandlerMethod method) {
        return method.hasMethodAnnotation(CentralOnly.class)
                || method.getBeanType().isAnnotationPresent(CentralOnly.class);
    }
}
