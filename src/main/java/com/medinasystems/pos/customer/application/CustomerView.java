package com.medinasystems.pos.customer.application;

import java.time.Instant;
import java.util.Objects;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.domain.Customer;
import com.medinasystems.pos.customer.domain.CustomerType;
import com.medinasystems.pos.customer.domain.DocumentType;

/** A customer as the API shows it. {@code finalConsumer} marks the seeded final consumer, which cannot be changed. */
public record CustomerView(Long id, String externalCode, CustomerType customerType, DocumentType documentType,
                           String documentNumber, String firstName, String middleName, String lastName,
                           String secondLastName, String businessName, String displayName, String phone, String email,
                           String address, Long priceListId, boolean active, boolean finalConsumer, Instant createdAt,
                           Instant updatedAt, Integer version) {

    public static CustomerView from(Customer customer) {
        return new CustomerView(customer.getId(), customer.getExternalCode(), customer.getCustomerType(),
                customer.getDocumentType(), customer.getDocumentNumber(), customer.getFirstName(),
                customer.getMiddleName(), customer.getLastName(), customer.getSecondLastName(),
                customer.getBusinessName(), customer.getDisplayName(), customer.getPhone(), customer.getEmail(),
                customer.getAddress(), customer.getPriceListId(), customer.isActive(),
                Objects.equals(customer.getId(), CustomerDirectory.FINAL_CONSUMER_ID), customer.getCreatedAt(),
                customer.getUpdatedAt(), customer.getVersion());
    }
}
