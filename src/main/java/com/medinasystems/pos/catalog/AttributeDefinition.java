package com.medinasystems.pos.catalog;

import java.util.List;

/**
 * One active characteristic of a category template, as other modules read it. {@code dataType} is TEXT, NUMBER, DATE or
 * LIST; {@code options} is set only for LIST.
 */
public record AttributeDefinition(String code, String name, String dataType, List<String> options, boolean required) {
}
