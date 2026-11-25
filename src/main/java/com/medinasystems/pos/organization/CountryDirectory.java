package com.medinasystems.pos.organization;

import java.time.ZoneId;
import java.util.Optional;

/**
 * Public API of the organization module for other modules. It answers questions about
 * countries without exposing the {@code Country} entity.
 */
public interface CountryDirectory {

    /** True when the country exists and is active. An unknown id answers false. */
    boolean isActive(long countryId);

    /** Id of the country with this ISO code (for example "HN"), active or not. Empty when there is none. */
    Optional<Long> findIdByIsoCode(String isoCode);

    /**
     * Time zone of the country, used to decide what "today" means for it. Stores do not replicate countries, so when
     * the country row is missing the time zone of the company is used. Empty when neither exists.
     */
    Optional<ZoneId> timeZoneOf(long countryId);
}
