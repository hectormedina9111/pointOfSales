package com.medinasystems.pos.audit.application;

import java.time.Clock;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.medinasystems.pos.audit.domain.AuditLogEntry;
import com.medinasystems.pos.audit.infrastructure.AuditLogRepository;
import com.medinasystems.pos.shared.AuditEvent;

/**
 * Stores each {@link AuditEvent}. It is a synchronous listener, so the row is written in the
 * transaction of the change: if the change rolls back, its audit row rolls back too.
 */
@Component
public class AuditListener {

    private final AuditLogRepository log;
    private final Clock clock;

    public AuditListener(AuditLogRepository log, Clock clock) {
        this.log = log;
        this.clock = clock;
    }

    @EventListener
    public void record(AuditEvent event) {
        Long userId = null;
        String username = null;
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            username = jwt.getName();
            if (jwt.getToken().getClaim("uid") instanceof Number uid) {
                userId = uid.longValue();
            }
        }
        log.save(new AuditLogEntry(clock.instant(), userId, username, event.action(), event.entity(),
                event.entityId(), event.oldValues(), event.newValues(), remoteAddress()));
    }

    private static String remoteAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
