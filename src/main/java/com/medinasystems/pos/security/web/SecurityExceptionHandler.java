package com.medinasystems.pos.security.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.medinasystems.pos.security.LoginNotAllowedException;
import com.medinasystems.pos.security.application.AccountLockedException;
import com.medinasystems.pos.security.application.InvalidCredentialsException;

/** Maps authentication failures to RFC 9457 problem details, with texts from the message bundles. */
@RestControllerAdvice
public class SecurityExceptionHandler {

    private final MessageSource messages;

    public SecurityExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                messages.getMessage("error.INVALID_CREDENTIALS", null, LocaleContextHolder.getLocale()));
        problem.setTitle(messages.getMessage("title.INVALID_CREDENTIALS", null, LocaleContextHolder.getLocale()));
        return problem;
    }

    /** The password was right, but this node does not serve the user (for example, a store and another branch). */
    @ExceptionHandler(LoginNotAllowedException.class)
    public ProblemDetail handleLoginNotAllowed(LoginNotAllowedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                messages.getMessage("error." + ex.getCode(), null, ex.getMessage(),
                        LocaleContextHolder.getLocale()));
        problem.setTitle(messages.getMessage("title.FORBIDDEN", null, LocaleContextHolder.getLocale()));
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(AccountLockedException.class)
    public ProblemDetail handleAccountLocked(AccountLockedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(423),
                messages.getMessage("error.ACCOUNT_LOCKED", null, LocaleContextHolder.getLocale()));
        problem.setTitle(messages.getMessage("title.ACCOUNT_LOCKED", null, LocaleContextHolder.getLocale()));
        return problem;
    }
}
