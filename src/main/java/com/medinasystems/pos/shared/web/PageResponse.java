package com.medinasystems.pos.shared.web;

import java.util.List;

import org.springframework.data.domain.Page;

/** A page of results without Spring Data's internal layout, so the JSON stays stable. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
