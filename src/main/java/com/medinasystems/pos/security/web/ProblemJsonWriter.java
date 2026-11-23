package com.medinasystems.pos.security.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes RFC 9457 bodies from filters and security handlers, where Spring MVC's
 * exception handling is not available. Titles and details are fixed constants.
 */
final class ProblemJsonWriter {

    private ProblemJsonWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
                        .formatted(title, status.value(), detail));
    }
}
