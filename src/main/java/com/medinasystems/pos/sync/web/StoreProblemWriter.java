package com.medinasystems.pos.sync.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes RFC 9457 bodies from the store-token filter, where Spring MVC's exception handling is not available.
 * The same format as the security module's writer; that one is internal to its module, so this is a small copy.
 */
@Component
class StoreProblemWriter {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messages;

    StoreProblemWriter(MessageSource messages) {
        this.messages = messages;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
               String titleKey, String detailKey) throws IOException {
        Locale locale = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null ? DEFAULT_LOCALE : request.getLocale();
        String title = messages.getMessage(titleKey, null, locale);
        String detail = messages.getMessage(detailKey, null, locale);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}"
                .formatted(escape(title), status.value(), escape(detail), escape(code)));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
