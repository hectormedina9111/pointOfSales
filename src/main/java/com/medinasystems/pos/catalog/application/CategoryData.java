package com.medinasystems.pos.catalog.application;

/** Input for creating or changing a category. A null parent makes it a root category. */
public record CategoryData(String name, Long parentId, boolean active) {
}
