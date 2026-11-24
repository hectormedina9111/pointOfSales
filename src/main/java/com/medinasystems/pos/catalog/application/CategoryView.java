package com.medinasystems.pos.catalog.application;

import com.medinasystems.pos.catalog.domain.Category;

public record CategoryView(Long id, Long parentId, String name, boolean active) {

    public static CategoryView from(Category category) {
        Category parent = category.getParent();
        return new CategoryView(category.getId(), parent == null ? null : parent.getId(), category.getName(),
                category.isActive());
    }
}
