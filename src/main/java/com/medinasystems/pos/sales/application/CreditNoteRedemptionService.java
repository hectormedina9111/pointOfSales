package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.CreditNoteRedemption;
import com.medinasystems.pos.sales.infrastructure.CreditNoteRedemptionRepository;
import com.medinasystems.pos.sales.infrastructure.CreditNoteRepository;
import com.medinasystems.pos.shared.BusinessConflictException;
import com.medinasystems.pos.shared.BusinessNotFoundException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * The central node's side of credit notes: the balance a store looks up, and the redemptions that take it, release it
 * and confirm it. Every change to a balance locks the note first and then reads the redemption, so two requests for the
 * same note or key run one after the other. Stores call it through the sync endpoints; upload handlers call it for the
 * sales and releases that stores send later.
 */
@Service
public class CreditNoteRedemptionService {

    /** Result of a release: released now, nothing to release, or already confirmed by a sale (not released). */
    public enum ReleaseOutcome {
        RELEASED, NOT_APPLIED, CONFIRMED
    }

    private final CreditNoteRepository notes;
    private final CreditNoteRedemptionRepository redemptions;
    private final CustomerDirectory customers;

    public CreditNoteRedemptionService(CreditNoteRepository notes, CreditNoteRedemptionRepository redemptions,
                                       CustomerDirectory customers) {
        this.notes = notes;
        this.redemptions = redemptions;
        this.customers = customers;
    }

    /** The note with this number and its current balance.
     *
     * @throws BusinessNotFoundException CREDIT_NOTE_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public CreditNoteBalanceView balance(String number) {
        CreditNote note = notes.findByNumber(number).orElseThrow(CreditNoteRedemptionService::notFound);
        String name = customers.find(note.getCustomerId()).map(CustomerInfo::displayName).orElse("");
        return CreditNoteBalanceView.of(note, name);
    }

    /**
     * Takes {@code amount} from the note for one payment. The same key asked again returns the redemption it made, and
     * takes nothing more; a key used for another note, amount or store is a conflict.
     *
     * @throws BusinessNotFoundException CREDIT_NOTE_NOT_FOUND
     * @throws BusinessConflictException CREDIT_NOTE_REDEMPTION_CONFLICT
     * @throws com.medinasystems.pos.shared.BusinessRuleException CREDIT_NOTE_INSUFFICIENT_BALANCE
     */
    @Transactional
    public RedemptionResult redeem(String number, String redemptionKey, BigDecimal amount, long storeNodeId) {
        CreditNote note = notes.findByNumberForUpdate(number).orElseThrow(CreditNoteRedemptionService::notFound);
        Optional<CreditNoteRedemption> existing = redemptions.findByRedemptionKey(redemptionKey);
        if (existing.isEmpty()) {
            note.spend(amount);
            return RedemptionResult.of(
                    redemptions.save(new CreditNoteRedemption(note.getId(), redemptionKey, amount, storeNodeId)), note);
        }
        CreditNoteRedemption found = existing.get();
        if (!found.isSameRequest(note.getId(), amount, storeNodeId)) {
            throw new BusinessConflictException("CREDIT_NOTE_REDEMPTION_CONFLICT",
                    "The redemption key is already used by another payment");
        }
        if (!found.isApplied()) {
            // Released by a failed payment and asked again with the same key: the amount is taken again.
            note.spend(amount);
            found.reapply();
        }
        return RedemptionResult.of(found, note);
    }

    /**
     * Gives back the amount of a payment that failed. A key that is unknown, already released, or of another store is a
     * no-op. A redemption confirmed by a sale cannot be released.
     */
    @Transactional
    public ReleaseOutcome release(String redemptionKey, long storeNodeId) {
        Optional<Long> noteId = redemptions.findCreditNoteIdByRedemptionKey(redemptionKey);
        if (noteId.isEmpty()) {
            return ReleaseOutcome.NOT_APPLIED;
        }
        CreditNote note = notes.findByIdForUpdate(noteId.get()).orElseThrow(() -> new ResourceNotFoundException(
                "credit_note", noteId.get()));
        Optional<CreditNoteRedemption> found = redemptions.findByRedemptionKey(redemptionKey)
                .filter(redemption -> Objects.equals(redemption.getStoreNodeId(), storeNodeId));
        if (found.isEmpty() || found.get().isReleased()) {
            return ReleaseOutcome.NOT_APPLIED;
        }
        CreditNoteRedemption redemption = found.get();
        if (redemption.isConfirmed()) {
            return ReleaseOutcome.CONFIRMED;
        }
        note.restore(redemption.getAmount());
        redemption.release();
        return ReleaseOutcome.RELEASED;
    }

    /**
     * Records that the sale that used this redemption reached the central node. Returns false when the redemption is
     * missing, belongs to another store, or was released; the caller accepts the sale in every case.
     */
    @Transactional
    public boolean confirm(String redemptionKey, long storeNodeId, long saleId) {
        Optional<Long> noteId = redemptions.findCreditNoteIdByRedemptionKey(redemptionKey);
        if (noteId.isEmpty()) {
            return false;
        }
        notes.findByIdForUpdate(noteId.get());
        Optional<CreditNoteRedemption> found = redemptions.findByRedemptionKey(redemptionKey)
                .filter(redemption -> Objects.equals(redemption.getStoreNodeId(), storeNodeId));
        if (found.isEmpty() || found.get().isReleased()) {
            return false;
        }
        found.get().confirm(saleId);
        return true;
    }

    private static BusinessNotFoundException notFound() {
        return new BusinessNotFoundException("CREDIT_NOTE_NOT_FOUND", "The credit note does not exist");
    }

    /** The redemptions of one note, newest first, paged in the database. */
    @Transactional(readOnly = true)
    public Page<CreditNoteRedemptionView> redemptions(long creditNoteId, Pageable pageable) {
        if (!notes.existsById(creditNoteId)) {
            throw new ResourceNotFoundException("credit_note", creditNoteId);
        }
        return redemptions.findByCreditNoteId(creditNoteId, pageable).map(CreditNoteRedemptionView::from);
    }
}
