package com.medinasystems.pos.catalog.application;

import java.util.List;

import com.medinasystems.pos.catalog.domain.CategoryAttribute;

/** A characteristic of a category template. Inactive ones are listed too, with their flag. */
public record CategoryAttributeView(Long id, Long categoryId, String code, String name, String dataType,
                                    List<String> options, boolean required, int sortOrder, boolean active) {

    public static CategoryAttributeView from(CategoryAttribute attribute) {
        return new CategoryAttributeView(attribute.getId(), attribute.getCategory().getId(), attribute.getCode(),
                attribute.getName(), attribute.getDataType().name(), attribute.getOptions(), attribute.isRequired(),
                attribute.getSortOrder(), attribute.isActive());
    }
}
