package com.medinasystems.pos.sales.application;

import com.medinasystems.pos.sales.domain.PaymentMethod;

public record PaymentMethodView(Long id, String code, String name, boolean requiresReference, boolean allowsChange,
                                boolean active) {

    public static PaymentMethodView from(PaymentMethod method) {
        return new PaymentMethodView(method.getId(), method.getCode(), method.getName(),
                method.isRequiresReference(), method.isAllowsChange(), method.isActive());
    }
}
