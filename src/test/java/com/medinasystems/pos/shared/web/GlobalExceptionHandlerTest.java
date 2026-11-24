package com.medinasystems.pos.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsResourceNotFoundTo404() {
        ProblemDetail problem = handler.handleResourceNotFound(new ResourceNotFoundException("product", 42L));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("product 42 not found");
        assertThat(problem.getProperties())
                .containsEntry("resource", "product")
                .containsEntry("id", 42L);
    }

    @Test
    void mapsBusinessRuleExceptionTo422() {
        ProblemDetail problem = handler.handleBusinessRule(
                new BusinessRuleException("product.sku.duplicated", "SKU already exists"));

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getTitle()).isEqualTo("Business rule violated");
        assertThat(problem.getDetail()).isEqualTo("SKU already exists");
        assertThat(problem.getProperties()).containsEntry("code", "product.sku.duplicated");
    }
}
