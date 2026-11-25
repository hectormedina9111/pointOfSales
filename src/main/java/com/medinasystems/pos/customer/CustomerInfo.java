package com.medinasystems.pos.customer;

/**
 * What a sale or a ticket needs about a customer. {@code priceListId} is null when the customer has no price list of
 * its own; {@code documentType} is the code of the document (RTN, DNI, PASSPORT or NONE).
 */
public record CustomerInfo(long id, String displayName, String documentType, String documentNumber, Long priceListId,
                           boolean active) {
}
