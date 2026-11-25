package com.medinasystems.pos.shared.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.medinasystems.pos.shared.BusinessConflictException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ReadOnlyNodeException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.shared.StoreOnlyOperationException;

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
        problem.setProperty("code", "NOT_FOUND");
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

    @ExceptionHandler(BusinessConflictException.class)
    public ProblemDetail handleBusinessConflict(BusinessConflictException ex) {
        String detail = messages.getMessage("error." + ex.getCode(), null, ex.getMessage(),
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle(messages.getMessage("title.BUSINESS_CONFLICT", null, LocaleContextHolder.getLocale()));
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

    @ExceptionHandler(StoreOnlyOperationException.class)
    public ProblemDetail handleStoreOnly(StoreOnlyOperationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                messages.getMessage("error.STORE_ONLY_OPERATION", null, LocaleContextHolder.getLocale()));
        problem.setTitle(messages.getMessage("title.STORE_ONLY_OPERATION", null, LocaleContextHolder.getLocale()));
        problem.setProperty("code", "STORE_ONLY_OPERATION");
        return problem;
    }

    /** Keeps the standard field errors; only the general detail is translated. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);
        if (response.getBody() instanceof ProblemDetail problem) {
            Locale locale = LocaleContextHolder.getLocale();
            problem.setDetail(messages.getMessage("error.VALIDATION_FAILED", null, locale));
            problem.setProperty("code", "VALIDATION_FAILED");

            List<Map<String, Object>> errors = new ArrayList<>();
            for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("field", fieldError.getField());
                error.put("message", messages.getMessage(fieldError, locale));
                errors.add(error);
            }
            for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("field", null);
                error.put("message", messages.getMessage(globalError, locale));
                errors.add(error);
            }
            problem.setProperty("errors", errors);
        }
        return response;
    }
}
