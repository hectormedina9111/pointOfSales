package com.medinasystems.pos.security.web;

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
 * Writes RFC 9457 bodies from filters, interceptors and security handlers, where
 * Spring MVC's exception handling is not available. Titles and details are keys in
 * the message bundles, resolved in the language of the request: Accept-Language, or
 * Spanish when the request has no such header.
 */
@Component
final class ProblemJsonWriter {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es");

    private final MessageSource messages;

    ProblemJsonWriter(MessageSource messages) {
        this.messages = messages;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
               String titleKey, String detailKey, Object... args) throws IOException {
        Locale locale = localeOf(request);
        String title = messages.getMessage(titleKey, null, locale);
        String detail = messages.getMessage(detailKey, args.length == 0 ? null : args, locale);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Set before getWriter(), so the body is written in UTF-8 and the Spanish texts are not garbled.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}"
                .formatted(escape(title), status.value(), escape(detail), escape(code)));
    }

    private static Locale localeOf(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null) {
            return DEFAULT_LOCALE;
        }
        return request.getLocale();
    }

    /** Escapes the characters that cannot appear raw inside a JSON string. */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
