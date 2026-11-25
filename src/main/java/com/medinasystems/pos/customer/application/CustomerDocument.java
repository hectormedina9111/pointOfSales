package com.medinasystems.pos.customer.application;

import java.time.Instant;

import com.medinasystems.pos.customer.domain.Customer;

/**
 * A customer created in a store, as the store uploads it (event CUSTOMER_CREATED). It carries the store's own id, which
 * the central node keeps. Field names are part of the protocol; the central node reads them in
 * {@link CustomerUploadHandler}.
 */
public record CustomerDocument(long id, String externalCode, String customerType, String documentType,
                               String documentNumber, String firstName, String middleName, String lastName,
                               String secondLastName, String businessName, String phone, String email, String address,
                               Long priceListId, boolean active, String createdAt, String createdBy, String updatedAt,
                               String updatedBy, Integer version) {

    public static final String EVENT_TYPE = "CUSTOMER_CREATED";

    public static CustomerDocument of(Customer customer) {
        return new CustomerDocument(customer.getId(), customer.getExternalCode(),
                customer.getCustomerType().name(), customer.getDocumentType().name(), customer.getDocumentNumber(),
                customer.getFirstName(), customer.getMiddleName(), customer.getLastName(),
                customer.getSecondLastName(), customer.getBusinessName(), customer.getPhone(), customer.getEmail(),
                customer.getAddress(), customer.getPriceListId(), customer.isActive(),
                text(customer.getCreatedAt()), customer.getCreatedBy(), text(customer.getUpdatedAt()),
                customer.getUpdatedBy(), customer.getVersion());
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
