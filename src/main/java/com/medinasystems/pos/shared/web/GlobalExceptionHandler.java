package com.medinasystems.pos.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ReadOnlyNodeException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Maps shared exceptions to RFC 9457 problem details. Standard Spring MVC
 * errors are handled by the parent class.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleResourceNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        problem.setProperty("resource", ex.getResource());
        problem.setProperty("id", ex.getId());
        return problem;
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), ex.getMessage());
        problem.setTitle("Business rule violated");
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(ReadOnlyNodeException.class)
    public ProblemDetail handleReadOnlyNode(ReadOnlyNodeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Read-only node");
        problem.setProperty("code", "READ_ONLY_NODE");
        return problem;
    }
}
