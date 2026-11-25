package com.medinasystems.pos.customer.domain;

import java.util.Objects;

import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * The editable data of a customer, normalized and checked when it is built. Blank text becomes null, the names that do
 * not apply to the customer type are cleared, and the document number is cleared when there is no document. Rules that
 * need the database (uniqueness of the document and of the external code) are checked by the service.
 */
public record CustomerDetails(String externalCode, CustomerType customerType, DocumentType documentType,
                              String documentNumber, String firstName, String middleName, String lastName,
                              String secondLastName, String businessName, String phone, String email, String address,
                              Long priceListId, boolean active) {

    /** Column sizes of the customer table (V16). */
    static final int MAX_EXTERNAL_CODE = 50;
    static final int MAX_DOCUMENT_NUMBER = 20;
    static final int MAX_NAME = 60;
    static final int MAX_BUSINESS_NAME = 200;
    static final int MAX_PHONE = 20;
    static final int MAX_EMAIL = 150;
    static final int MAX_ADDRESS = 300;

    public CustomerDetails {
        Objects.requireNonNull(customerType, "customerType is required");
        Objects.requireNonNull(documentType, "documentType is required");
        externalCode = limit(blankToNull(externalCode), MAX_EXTERNAL_CODE);
        documentNumber = limit(blankToNull(documentNumber), MAX_DOCUMENT_NUMBER);
        firstName = limit(blankToNull(firstName), MAX_NAME);
        middleName = limit(blankToNull(middleName), MAX_NAME);
        lastName = limit(blankToNull(lastName), MAX_NAME);
        secondLastName = limit(blankToNull(secondLastName), MAX_NAME);
        businessName = limit(blankToNull(businessName), MAX_BUSINESS_NAME);
        phone = limit(blankToNull(phone), MAX_PHONE);
        email = limit(blankToNull(email), MAX_EMAIL);
        address = limit(blankToNull(address), MAX_ADDRESS);

        if (customerType == CustomerType.PERSON) {
            if (firstName == null || lastName == null) {
                throw new BusinessRuleException("CUSTOMER_NAME_REQUIRED",
                        "A person needs a first name and a last name");
            }
            businessName = null;
        } else {
            if (businessName == null) {
                throw new BusinessRuleException("CUSTOMER_BUSINESS_NAME_REQUIRED",
                        "A company needs a business name");
            }
            firstName = null;
            middleName = null;
            lastName = null;
            secondLastName = null;
        }

        if (documentType == DocumentType.NONE) {
            documentNumber = null;
        } else if (documentNumber == null) {
            throw new BusinessRuleException("CUSTOMER_DOCUMENT_NUMBER_REQUIRED",
                    "The document number is required for this document type");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int max) {
        if (value != null && value.length() > max) {
            throw new BusinessRuleException("CUSTOMER_FIELD_TOO_LONG",
                    "A field is longer than its maximum length");
        }
        return value;
    }
}
