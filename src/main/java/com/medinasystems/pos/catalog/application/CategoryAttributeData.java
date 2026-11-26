package com.medinasystems.pos.catalog.application;

import java.util.List;

import com.medinasystems.pos.catalog.domain.AttributeDataType;

/** Input for creating or changing a characteristic of a category template. */
public record CategoryAttributeData(String code, String name, AttributeDataType dataType, List<String> options,
                                    boolean required, int sortOrder, boolean active) {
}
