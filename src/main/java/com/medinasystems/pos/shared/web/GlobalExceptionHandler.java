package com.medinasystems.pos.shared.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ReadOnlyNodeException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Maps shared exceptions to RFC 9457 problem details. Standard Spring MVC
 * errors are handled by the parent class. Texts come from messages*.properties,
 * in the locale of the request (Accept-Language, Spanish by default); the
 * {@code code} property keeps its English identifier.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        String detail = messages.getMessage("error.NOT_FOUND",
                new Object[] {ex.getResource(), String.valueOf(ex.getId())}, LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle(messages.getMessage("title.NOT_FOUND", null, LocaleContextHolder.getLocale()));
        problem.setProperty("resource", ex.getResource());
        problem.setProperty("id", ex.getId());
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        // The text comes from messages*.properties under "error.<CODE>"; without a translation, the exception message is used.
        String detail = messages.getMessage("error." + ex.getCode(), null, ex.getMessage(),
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), detail);
        problem.setTitle(messages.getMessage("title.BUSINESS_RULE_VIOLATED", null, LocaleContextHolder.getLocale()));
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(ReadOnlyNodeException.class)
    public ProblemDetail handleReadOnlyNode(ReadOnlyNodeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                messages.getMessage("error.READ_ONLY_NODE", null, LocaleContextHolder.getLocale()));
        problem.setTitle(messages.getMessage("title.READ_ONLY_NODE", null, LocaleContextHolder.getLocale()));
        problem.setProperty("code", "READ_ONLY_NODE");
        return problem;
    }

    /** Keeps the standard field errors; only the general detail is translated. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);
        if (response.getBody() instanceof ProblemDetail problem) {
            problem.setDetail(messages.getMessage("error.VALIDATION_FAILED", null, LocaleContextHolder.getLocale()));
        }
        return response;
    }
}
