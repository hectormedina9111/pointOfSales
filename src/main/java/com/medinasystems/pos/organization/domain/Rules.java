package com.medinasystems.pos.organization.domain;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.regex.Pattern;

import com.medinasystems.pos.shared.BusinessRuleException;

/** Validation shared by the organization entities. */
final class Rules {

    static final Pattern ISO_CODE = Pattern.compile("[A-Z]{2}");
    static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private Rules() {
    }

    static String requireIsoCode(String value) {
        return requirePattern(value, ISO_CODE, "INVALID_ISO_CODE", "The ISO country code must be two uppercase letters");
    }

    static String requireCurrency(String value) {
        return requirePattern(value, CURRENCY, "INVALID_CURRENCY", "The currency must be three uppercase letters");
    }

    static String requireTimeZone(String value) {
        if (value == null) {
            throw new BusinessRuleException("INVALID_TIME_ZONE", "The time zone is required");
        }
        try {
            ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new BusinessRuleException("INVALID_TIME_ZONE", "The time zone is not a known zone id");
        }
        return value;
    }

    private static String requirePattern(String value, Pattern pattern, String code, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new BusinessRuleException(code, message);
        }
        return value;
    }
}
