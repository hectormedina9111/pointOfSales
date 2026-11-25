package com.medinasystems.pos.sales.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sales.application.PaymentMethodData;
import com.medinasystems.pos.sales.application.PaymentMethodService;
import com.medinasystems.pos.sales.application.PaymentMethodView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Payment methods. There is no POST: the codes are fixed by the system. PUT replaces the name and the flags,
 * so every field is required.
 */
@RestController
@RequestMapping("/api/v1/payment-methods")
public class PaymentMethodController {

    private final PaymentMethodService methods;

    public PaymentMethodController(PaymentMethodService methods) {
        this.methods = methods;
    }

    @GetMapping
    public PageResponse<PaymentMethodView> list(@RequestParam(required = false) Boolean active,
                                                @PageableDefault(sort = "code") Pageable pageable) {
        return PageResponse.from(methods.list(active, pageable));
    }

    @PutMapping("/{id}")
    @CentralOnly
    public PaymentMethodView update(@PathVariable Long id, @Valid @RequestBody PaymentMethodRequest request) {
        return methods.update(id, request.toData());
    }

    public record PaymentMethodRequest(@NotBlank @Size(max = 60) String name,
                                       @NotNull Boolean requiresReference,
                                       @NotNull Boolean allowsChange,
                                       @NotNull Boolean active) {

        PaymentMethodData toData() {
            return new PaymentMethodData(name, requiresReference, allowsChange, active);
        }
    }
}
