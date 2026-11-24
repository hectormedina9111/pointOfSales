package com.medinasystems.pos.organization;

/**
 * Public API of the organization module for other modules. It answers questions about
 * countries without exposing the {@code Country} entity.
 */
public interface CountryDirectory {

    /** True when the country exists and is active. An unknown id answers false. */
    boolean isActive(long countryId);
}
