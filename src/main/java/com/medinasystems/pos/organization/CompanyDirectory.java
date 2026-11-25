package com.medinasystems.pos.organization;

import java.util.Optional;

/**
 * Public API of the organization module for other modules: the company that owns the branches, as the ticket
 * needs it. The {@code Company} entity stays inside the module.
 */
public interface CompanyDirectory {

    /** The company of this installation. Empty when the company is not configured yet. */
    Optional<CompanyInfo> find();
}
