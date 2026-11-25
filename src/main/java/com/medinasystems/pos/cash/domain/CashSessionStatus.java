package com.medinasystems.pos.cash.domain;

/** OPEN while the cashier works; CLOSED after the count. A closed session is never reopened. */
public enum CashSessionStatus {
    OPEN,
    CLOSED
}
