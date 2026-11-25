package com.medinasystems.pos.sales.domain;

/**
 * How a return is refunded. CASH and ORIGINAL_METHOD are given the same day as the sale, from the open register. CREDIT_NOTE
 * is issued as a document with a balance, on any later day.
 */
public enum RefundMethod {
    CASH,
    ORIGINAL_METHOD,
    CREDIT_NOTE
}
