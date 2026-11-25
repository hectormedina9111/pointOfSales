package com.medinasystems.pos.sales.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medinasystems.pos.shared.BusinessNotFoundException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.sales.domain.PaymentDraft;
import com.medinasystems.pos.sales.domain.PaymentRules;
import com.medinasystems.pos.sync.CentralGateway;
import com.medinasystems.pos.sync.CentralUnavailableException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Pays with a credit note. The balance lives on the central node, so the store asks it: a lookup for the cashier's screen,
 * and a redemption for each credit note payment of a charge. The redemption key of a payment is the charge's idempotency
 * key and the payment's position, so a repeated request takes nothing twice.
 *
 * <p>When the charge rolls back after its redemptions, each amount is given back: now, or through the outbox
 * ({@code CREDIT_NOTE_RELEASE}) when the central node cannot be reached. The central node answers a release of an unknown
 * key with a no-op, so releasing a redemption that was never applied is safe.
 *
 * <p>The central node has this bean too, so the application starts; a store is the only node whose charges reach it.
 */
@Service
public class CentralCreditNotes {

    /** Outbox event that gives a redemption back later, when the central node is not reachable now. */
    public static final String RELEASE_EVENT = "CREDIT_NOTE_RELEASE";

    private static final Logger log = LoggerFactory.getLogger(CentralCreditNotes.class);
    private static final String SYNC_PATH = "/api/v1/sync/credit-notes/";
    /** Credit note numbers are upper-case letters, digits and hyphens: they are safe in a URL path. */
    private static final Pattern NUMBER = Pattern.compile("[A-Z0-9-]{1,30}");
    /** Redemption keys are safe in a URL path: the idempotency key is checked against this before it is sent. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final CentralGateway central;
    private final Outbox outbox;

    public CentralCreditNotes(CentralGateway central, Outbox outbox) {
        this.central = central;
        this.outbox = outbox;
    }

    /** The number as it is compared and sent: trimmed and upper-case; null when blank. */
    public static String normalize(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        return number.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The note with this number and its balance, as the central node sees it now.
     *
     * @throws BusinessNotFoundException CREDIT_NOTE_NOT_FOUND, also for a number that cannot be a note number
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    public CreditNoteBalanceView balance(String number) {
        String normalized = normalize(number);
        if (normalized == null || !NUMBER.matcher(normalized).matches()) {
            throw new BusinessNotFoundException("CREDIT_NOTE_NOT_FOUND", "The credit note does not exist");
        }
        return central.get(SYNC_PATH + normalized, CreditNoteBalanceView.class);
    }

    /**
     * Takes the amount of every credit note payment at the central node. Returns the drafts with the note id and the
     * redemption key of each credit note payment. It must run inside the charge's transaction: when that transaction rolls
     * back, the amounts already taken are given back.
     *
     * @throws BusinessNotFoundException CREDIT_NOTE_NOT_FOUND
     * @throws BusinessRuleException INVALID_IDEMPOTENCY_KEY, CREDIT_NOTE_INSUFFICIENT_BALANCE (from the central node)
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    public List<PaymentDraft> redeem(List<PaymentDraft> drafts, String idempotencyKey) {
        if (drafts.stream().noneMatch(CentralCreditNotes::isCreditNote)) {
            return drafts;
        }
        List<String> keys = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    keys.forEach(CentralCreditNotes.this::release);
                }
            }
        });
        List<PaymentDraft> redeemed = new ArrayList<>(drafts.size());
        for (int position = 0; position < drafts.size(); position++) {
            PaymentDraft draft = drafts.get(position);
            if (!isCreditNote(draft)) {
                redeemed.add(draft);
                continue;
            }
            String key = idempotencyKey + ":" + position;
            if (!KEY.matcher(key).matches()) {
                throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY",
                        "The idempotency key has characters that a credit note payment cannot use");
            }
            String number = draft.creditNoteNumber();
            if (number == null || !NUMBER.matcher(number).matches()) {
                throw new BusinessNotFoundException("CREDIT_NOTE_NOT_FOUND", "The credit note does not exist");
            }
            // The key is recorded before the call: if the answer is lost, the release still covers it.
            keys.add(key);
            RedemptionResult taken = central.post(SYNC_PATH + number + "/redemptions",
                    Map.of("redemptionKey", key, "amount", draft.amount()), RedemptionResult.class);
            redeemed.add(draft.withCreditNote(taken.creditNoteId(), key));
        }
        return redeemed;
    }

    /**
     * Gives back the amount of one redemption. An unreachable central node queues the release in the outbox; a refusal
     * is logged without its details.
     */
    void release(String redemptionKey) {
        try {
            central.post(SYNC_PATH + "redemptions/" + redemptionKey + "/release", null, ReleaseResult.class);
        } catch (CentralUnavailableException ex) {
            outbox.enqueue(RELEASE_EVENT, Map.of("redemptionKey", redemptionKey));
        } catch (RuntimeException ex) {
            log.warn("A credit note redemption was not released: {}", ex.getClass().getSimpleName());
        }
    }

    private static boolean isCreditNote(PaymentDraft draft) {
        return PaymentRules.CREDIT_NOTE.equals(draft.method().getCode());
    }
}
