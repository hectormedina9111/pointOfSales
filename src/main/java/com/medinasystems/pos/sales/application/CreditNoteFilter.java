package com.medinasystems.pos.sales.application;

import java.time.Instant;

import com.medinasystems.pos.sales.domain.CreditNoteStatus;

/**
 * Filters of the credit note list. A null filter does not restrict the result. {@code branchId} is replaced by the
 * visibility rules of the node through {@link #withBranch}. {@code search} matches the number and is trimmed.
 */
public record CreditNoteFilter(Long customerId, CreditNoteStatus status, String search, Long branchId, Instant from,
                               Instant to) {

    public CreditNoteFilter {
        search = search == null || search.isBlank() ? null : search.strip();
    }

    public CreditNoteFilter withBranch(Long scopedBranchId) {
        return new CreditNoteFilter(customerId, status, search, scopedBranchId, from, to);
    }
}
