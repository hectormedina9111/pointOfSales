package com.medinasystems.pos.catalog.application;

/** Input for creating or changing a unit of measure. */
public record UnitOfMeasureData(String code, String name, boolean allowsDecimals, boolean active) {
}
