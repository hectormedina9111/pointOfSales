package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.medinasystems.pos.shared.BusinessNotFoundException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.sales.domain.ReturnLineRequest;
import com.medinasystems.pos.sync.CentralGateway;
import com.medinasystems.pos.sync.CentralUnavailableException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Return calls that a store makes to the central node: the returnable view of a sale of another branch, and the claims
 * of a return (reserve, and release when the return fails). The store always starts the call.
 *
 * <p>A release that the central node cannot take now goes to the outbox ({@code RETURN_CLAIM_RELEASE}); the central node
 * answers a release of a key with nothing reserved as a no-op, so a late release is safe.
 *
 * <p>The central node has this bean too, so the application starts; a store is the only node whose returns reach it.
 */
@Service
public class CentralReturns {

    /** Outbox event that gives a return's claims back later, when the central node is not reachable now. */
    public static final String RELEASE_EVENT = "RETURN_CLAIM_RELEASE";

    private static final Logger log = LoggerFactory.getLogger(CentralReturns.class);
    private static final String SYNC_PATH = "/api/v1/sync/sales/";
    /** Receipt numbers travel in the query string: only letters, digits, hyphens and underscores are sent. */
    private static final Pattern RECEIPT = Pattern.compile("[A-Za-z0-9_-]{1,30}");
    /** Claim keys travel in the URL path: the idempotency key is checked against this before it is sent. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private final CentralGateway central;
    private final Outbox outbox;

    public CentralReturns(CentralGateway central, Outbox outbox) {
        this.central = central;
        this.outbox = outbox;
    }

    /**
     * The returnable view of a sale of another branch, found by its receipt number.
     *
     * @throws BusinessNotFoundException SALE_NOT_FOUND (from the central node, or for a number that cannot be a receipt)
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    public ReturnableView byReceipt(String receiptNumber) {
        if (receiptNumber == null || !RECEIPT.matcher(receiptNumber).matches()) {
            throw new BusinessNotFoundException("SALE_NOT_FOUND", "The sale does not exist");
        }
        return central.get(SYNC_PATH + "returnable?receiptNumber=" + receiptNumber, ReturnableView.class);
    }

    /**
     * The returnable view of a sale of another branch, by id.
     *
     * @throws BusinessNotFoundException SALE_NOT_FOUND (from the central node)
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    public ReturnableView byId(long saleId) {
        return central.get(SYNC_PATH + saleId + "/returnable", ReturnableView.class);
    }

    /**
     * Holds the quantities of a return at the central node. Throws what the central node answers: a quantity that no
     * longer fits is RETURN_QUANTITY_EXCEEDED.
     *
     * @throws BusinessRuleException INVALID_IDEMPOTENCY_KEY, or a rule answered by the central node
     * @throws CentralUnavailableException when the central node cannot be reached
     */
    public void reserve(long saleId, String claimKey, List<ReturnLineRequest> lines) {
        checkKey(claimKey);
        List<Map<String, Object>> body = lines.stream()
                .map(line -> Map.<String, Object>of("saleLineId", line.saleLineId(), "quantity", line.quantity()))
                .toList();
        central.post(SYNC_PATH + saleId + "/return-claims", Map.of("claimKey", claimKey, "lines", body),
                ReturnableView.class);
    }

    /**
     * Gives back the reserved quantities of a return that failed. An unreachable central node queues the release in the
     * outbox; a refusal is logged without its details.
     */
    public void release(String claimKey) {
        try {
            checkKey(claimKey);
            central.post(SYNC_PATH + "return-claims/" + claimKey + "/release", null, ReleaseClaimsResult.class);
        } catch (CentralUnavailableException ex) {
            outbox.enqueue(RELEASE_EVENT, Map.of("claimKey", claimKey));
        } catch (RuntimeException ex) {
            log.warn("Return claims were not released: {}", ex.getClass().getSimpleName());
        }
    }

    private static void checkKey(String claimKey) {
        if (claimKey == null || !KEY.matcher(claimKey).matches()) {
            throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY",
                    "The idempotency key has characters that a return cannot use");
        }
    }
}
