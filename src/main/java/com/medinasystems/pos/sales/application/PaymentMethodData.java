package com.medinasystems.pos.sales.application;

/** Input for changing a payment method. The code is not part of it: it is fixed by the system. */
public record PaymentMethodData(String name, boolean requiresReference, boolean allowsChange, boolean active) {
}
