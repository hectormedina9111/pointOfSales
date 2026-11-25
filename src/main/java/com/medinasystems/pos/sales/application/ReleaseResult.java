package com.medinasystems.pos.sales.application;

/** The answer to a release: the key and what the central node did (RELEASED or NOT_APPLIED). */
public record ReleaseResult(String redemptionKey, String status) {
}
