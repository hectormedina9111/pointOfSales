package com.medinasystems.pos.sync;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import tools.jackson.databind.JsonNode;

/**
 * Reads the fields of an upload payload for the handlers. Each reader throws {@link InvalidPayloadException} when a
 * required field is missing or has the wrong type, so a handler can answer REJECTED with INVALID_PAYLOAD before it
 * writes anything. Money is read from JSON numbers as BigDecimal, which keeps the cents exact.
 */
public final class UploadPayload {

    private UploadPayload() {
    }

    /** A required field is missing or has the wrong type. The message names the field, never the value. */
    public static final class InvalidPayloadException extends RuntimeException {

        public InvalidPayloadException(String field) {
            super("Missing or invalid field: " + field);
        }
    }

    public static long longField(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isIntegralNumber()) {
            throw new InvalidPayloadException(name);
        }
        return value.longValue();
    }

    public static Long optionalLong(JsonNode node, String name) {
        return isAbsent(node, name) ? null : longField(node, name);
    }

    public static int intField(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isIntegralNumber()) {
            throw new InvalidPayloadException(name);
        }
        return value.intValue();
    }

    public static String text(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new InvalidPayloadException(name);
        }
        return value.textValue();
    }

    public static String optionalText(JsonNode node, String name) {
        if (isAbsent(node, name)) {
            return null;
        }
        JsonNode value = node.path(name);
        if (!value.isTextual()) {
            throw new InvalidPayloadException(name);
        }
        return value.textValue();
    }

    public static BigDecimal decimal(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isNumber()) {
            throw new InvalidPayloadException(name);
        }
        return value.decimalValue();
    }

    public static BigDecimal optionalDecimal(JsonNode node, String name) {
        return isAbsent(node, name) ? null : decimal(node, name);
    }

    public static boolean bool(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isBoolean()) {
            throw new InvalidPayloadException(name);
        }
        return value.booleanValue();
    }

    /** An instant written as ISO-8601 text, such as 2026-11-25T14:30:00Z. */
    public static Instant instant(JsonNode node, String name) {
        return parseInstant(text(node, name), name);
    }

    public static Instant optionalInstant(JsonNode node, String name) {
        String text = optionalText(node, name);
        return text == null ? null : parseInstant(text, name);
    }

    /** A required array. It may be empty; the caller decides whether emptiness is allowed. */
    public static JsonNode array(JsonNode node, String name) {
        JsonNode value = present(node, name);
        if (!value.isArray()) {
            throw new InvalidPayloadException(name);
        }
        return value;
    }

    private static JsonNode present(JsonNode node, String name) {
        if (isAbsent(node, name)) {
            throw new InvalidPayloadException(name);
        }
        return node.path(name);
    }

    private static boolean isAbsent(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isMissingNode() || value.isNull();
    }

    private static Instant parseInstant(String text, String name) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new InvalidPayloadException(name);
        }
    }
}
