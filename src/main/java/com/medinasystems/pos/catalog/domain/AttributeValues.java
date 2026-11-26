package com.medinasystems.pos.catalog.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.medinasystems.pos.catalog.AttributeDefinition;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * Checks the characteristics of one equipment against its category template. Pure rules, without persistence: unknown
 * codes are refused, required values must be present, and each value must fit its type. Numbers are stored as
 * {@link BigDecimal}, dates as ISO text (yyyy-MM-dd), the rest as trimmed text.
 */
public final class AttributeValues {

    /** Longest text value that the template accepts. */
    public static final int MAX_TEXT_LENGTH = 200;

    private AttributeValues() {
    }

    /**
     * The values normalized in the order of the template. A blank value counts as missing: it is omitted when the
     * characteristic is optional.
     *
     * @throws BusinessRuleException UNKNOWN_ATTRIBUTE, ATTRIBUTE_REQUIRED or INVALID_ATTRIBUTE_VALUE
     */
    public static Map<String, Object> normalize(List<AttributeDefinition> definitions, Map<String, ?> values) {
        Map<String, ?> input = values == null ? Map.of() : values;
        for (String code : input.keySet()) {
            if (definitions.stream().noneMatch(definition -> definition.code().equals(code))) {
                throw new BusinessRuleException("UNKNOWN_ATTRIBUTE", "The characteristic {0} is not in the template",
                        code);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (AttributeDefinition definition : definitions) {
            Object raw = input.get(definition.code());
            if (isBlank(raw)) {
                if (definition.required()) {
                    throw new BusinessRuleException("ATTRIBUTE_REQUIRED", "The characteristic {0} is required",
                            definition.name());
                }
                continue;
            }
            result.put(definition.code(), value(definition, raw));
        }
        return result;
    }

    private static Object value(AttributeDefinition definition, Object raw) {
        String type = definition.dataType();
        return switch (type) {
            case "NUMBER" -> number(definition, raw);
            case "DATE" -> date(definition, raw);
            case "LIST" -> list(definition, raw);
            default -> text(definition, raw);
        };
    }

    private static Object text(AttributeDefinition definition, Object raw) {
        if (!(raw instanceof String) && !(raw instanceof Number)) {
            throw invalid(definition);
        }
        String value = raw.toString().trim();
        if (value.length() > MAX_TEXT_LENGTH) {
            throw invalid(definition);
        }
        return value;
    }

    private static BigDecimal number(AttributeDefinition definition, Object raw) {
        if (raw instanceof Boolean) {
            throw invalid(definition);
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw invalid(definition);
        }
    }

    private static String date(AttributeDefinition definition, Object raw) {
        try {
            return LocalDate.parse(raw.toString().trim()).toString();
        } catch (DateTimeParseException ex) {
            throw invalid(definition);
        }
    }

    private static String list(AttributeDefinition definition, Object raw) {
        String value = raw instanceof String text ? text.trim() : null;
        if (value == null || !Objects.requireNonNullElse(definition.options(), List.<String>of()).contains(value)) {
            throw invalid(definition);
        }
        return value;
    }

    private static boolean isBlank(Object raw) {
        return raw == null || (raw instanceof String text && text.isBlank());
    }

    private static BusinessRuleException invalid(AttributeDefinition definition) {
        return new BusinessRuleException("INVALID_ATTRIBUTE_VALUE", "The value of {0} is not valid", definition.name());
    }
}
