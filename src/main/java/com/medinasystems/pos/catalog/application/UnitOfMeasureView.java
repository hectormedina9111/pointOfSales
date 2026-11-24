package com.medinasystems.pos.catalog.application;

import com.medinasystems.pos.catalog.domain.UnitOfMeasure;

public record UnitOfMeasureView(Long id, String code, String name, boolean allowsDecimals, boolean active) {

    public static UnitOfMeasureView from(UnitOfMeasure unit) {
        return new UnitOfMeasureView(unit.getId(), unit.getCode(), unit.getName(), unit.isAllowsDecimals(),
                unit.isActive());
    }
}
