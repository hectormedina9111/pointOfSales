package com.medinasystems.pos.sales.application;

import static com.medinasystems.pos.sync.UploadPayload.array;
import static com.medinasystems.pos.sync.UploadPayload.bool;
import static com.medinasystems.pos.sync.UploadPayload.decimal;
import static com.medinasystems.pos.sync.UploadPayload.instant;
import static com.medinasystems.pos.sync.UploadPayload.intField;
import static com.medinasystems.pos.sync.UploadPayload.longField;
import static com.medinasystems.pos.sync.UploadPayload.optionalDecimal;
import static com.medinasystems.pos.sync.UploadPayload.optionalLong;
import static com.medinasystems.pos.sync.UploadPayload.optionalText;
import static com.medinasystems.pos.sync.UploadPayload.text;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.cash.CashSessionDirectory;
import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: stores a paid sale uploaded by a store, with its lines and payments, under the store's own ids. A sale is
 * never revalidated, only checked for completeness and for the branch of the store. Sales are not changed after payment,
 * so a sale that already arrived is accepted without changes. When its cash session has not arrived yet, the item is
 * answered RETRY and nothing is written: the store sends it again in a later cycle. Everything is read and checked before
 * the first write.
 */
@Component
@Profile("central")
public class SaleUploadHandler implements UploadHandler {

    private static final String PAID = "PAID";

    private static final String SALE_COLUMNS = "id, type, internal_number, receipt_number, document_type, branch_id,"
            + " cash_register_id, cash_session_id, cashier_id, customer_id, order_type, status, is_exclusive, subtotal,"
            + " discount_total, exempt_amount, taxable_amount_15, taxable_amount_18, tax_total, total, amount_paid,"
            + " change_due, paid_at, discount_type, discount_value, discount_authorized_by, price_authorized_by, notes,"
            + " idempotency_key, fiscal_status, synced_at, created_at, created_by, updated_at, updated_by, version";

    private static final String LINE_COLUMNS = "id, sale_id, line_number, product_id, sku, description, quantity,"
            + " unit_price, list_price, price_overridden, unit_cost, discount_type, discount_value, discount_amount,"
            + " tax_code, tax_rate, tax_amount, subtotal, total, price_list_id, created_at, created_by, updated_at,"
            + " updated_by, version";

    private static final String PAYMENT_COLUMNS = "id, sale_id, payment_method_id, amount, amount_received, reference,"
            + " card_last4, paid_at, created_at, created_by, updated_at, updated_by, version";

    private final JdbcTemplate jdbc;
    private final CashSessionDirectory sessions;
    private final CustomerDirectory customers;
    private final StoreIdRanges ranges;
    private final Clock clock;

    public SaleUploadHandler(JdbcTemplate jdbc, CashSessionDirectory sessions, CustomerDirectory customers,
                             StoreIdRanges ranges, Clock clock) {
        this.jdbc = jdbc;
        this.sessions = sessions;
        this.customers = customers;
        this.ranges = ranges;
        this.clock = clock;
    }

    @Override
    public String eventType() {
        return SaleDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        try {
            return store(storeNodeId, payload, storeBranchId);
        } catch (InvalidPayloadException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
    }

    private UploadOutcome store(long storeNodeId, JsonNode payload, Long storeBranchId) {
        long id = longField(payload, "id");
        long branchId = longField(payload, "branchId");
        if (storeBranchId == null || !storeBranchId.equals(branchId)) {
            return UploadOutcome.rejected("BRANCH_MISMATCH");
        }
        if (!PAID.equals(text(payload, "status"))) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (!idsInRange(storeNodeId, payload)) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        long cashSessionId = longField(payload, "cashSessionId");
        long customerId = longField(payload, "customerId");
        Object[] sale = saleRow(payload, id, clock.instant());
        List<Object[]> lines = lineRows(id, array(payload, "lines"));
        List<Object[]> payments = paymentRows(id, array(payload, "payments"));
        if (lines.isEmpty() || payments.isEmpty()) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }

        Long stored = jdbc.queryForObject("SELECT count(*) FROM sale WHERE id = ?", Long.class, id);
        if (stored != null && stored > 0) {
            return UploadOutcome.accepted();
        }
        if (!sessions.exists(cashSessionId)) {
            return UploadOutcome.retry("CASH_SESSION_MISSING");
        }
        // The customer may still be on its way from the central node: the store sends the sale again later.
        if (customers.find(customerId).isEmpty()) {
            return UploadOutcome.retry("CUSTOMER_MISSING");
        }

        jdbc.update("INSERT INTO sale (" + SALE_COLUMNS + ") VALUES (" + placeholders(36) + ")", sale);
        String lineSql = "INSERT INTO sale_line (" + LINE_COLUMNS + ") VALUES (" + placeholders(25) + ")";
        for (Object[] line : lines) {
            jdbc.update(lineSql, line);
        }
        String paymentSql = "INSERT INTO payment (" + PAYMENT_COLUMNS + ") VALUES (" + placeholders(13) + ")";
        for (Object[] payment : payments) {
            jdbc.update(paymentSql, payment);
        }
        return UploadOutcome.accepted();
    }

    /**
     * Every id the store created for this sale lies in the store's range: the sale, its internal number, its lines and its
     * payments. Checked before anything is written.
     */
    private boolean idsInRange(long storeNodeId, JsonNode payload) {
        if (!ranges.contains(storeNodeId, longField(payload, "id"))
                || !ranges.contains(storeNodeId, longField(payload, "internalNumber"))) {
            return false;
        }
        for (JsonNode line : array(payload, "lines")) {
            if (!ranges.contains(storeNodeId, longField(line, "id"))) {
                return false;
            }
        }
        for (JsonNode payment : array(payload, "payments")) {
            if (!ranges.contains(storeNodeId, longField(payment, "id"))) {
                return false;
            }
        }
        return true;
    }

    /** The 36 values of SALE_COLUMNS, in that order. synced_at is the moment the central node received the sale. */
    private static Object[] saleRow(JsonNode p, long id, Instant receivedAt) {
        return new Object[] {
            id,
            text(p, "type"),
            longField(p, "internalNumber"),
            text(p, "receiptNumber"),
            text(p, "documentType"),
            longField(p, "branchId"),
            longField(p, "cashRegisterId"),
            longField(p, "cashSessionId"),
            longField(p, "cashierId"),
            longField(p, "customerId"),
            text(p, "orderType"),
            text(p, "status"),
            bool(p, "exclusive"),
            decimal(p, "subtotal"),
            decimal(p, "discountTotal"),
            decimal(p, "exemptAmount"),
            decimal(p, "taxableAmount15"),
            decimal(p, "taxableAmount18"),
            decimal(p, "taxTotal"),
            decimal(p, "total"),
            decimal(p, "amountPaid"),
            decimal(p, "changeDue"),
            timestamp(instant(p, "paidAt")),
            optionalText(p, "discountType"),
            optionalDecimal(p, "discountValue"),
            optionalLong(p, "discountAuthorizedBy"),
            optionalLong(p, "priceAuthorizedBy"),
            optionalText(p, "notes"),
            text(p, "idempotencyKey"),
            text(p, "fiscalStatus"),
            Timestamp.from(receivedAt),
            timestamp(instant(p, "createdAt")),
            text(p, "createdBy"),
            timestamp(instant(p, "updatedAt")),
            text(p, "updatedBy"),
            intField(p, "version")
        };
    }

    /** The 25 values of LINE_COLUMNS for each line. */
    private static List<Object[]> lineRows(long saleId, JsonNode lines) {
        List<Object[]> rows = new ArrayList<>(lines.size());
        for (JsonNode l : lines) {
            rows.add(new Object[] {
                longField(l, "id"),
                saleId,
                intField(l, "lineNumber"),
                longField(l, "productId"),
                text(l, "sku"),
                text(l, "description"),
                decimal(l, "quantity"),
                decimal(l, "unitPrice"),
                decimal(l, "listPrice"),
                bool(l, "priceOverridden"),
                optionalDecimal(l, "unitCost"),
                optionalText(l, "discountType"),
                optionalDecimal(l, "discountValue"),
                decimal(l, "discountAmount"),
                text(l, "taxCode"),
                decimal(l, "taxRate"),
                decimal(l, "taxAmount"),
                decimal(l, "subtotal"),
                decimal(l, "total"),
                optionalLong(l, "priceListId"),
                timestamp(instant(l, "createdAt")),
                text(l, "createdBy"),
                timestamp(instant(l, "updatedAt")),
                text(l, "updatedBy"),
                intField(l, "version")
            });
        }
        return rows;
    }

    /** The 13 values of PAYMENT_COLUMNS for each payment. */
    private static List<Object[]> paymentRows(long saleId, JsonNode payments) {
        List<Object[]> rows = new ArrayList<>(payments.size());
        for (JsonNode p : payments) {
            rows.add(new Object[] {
                longField(p, "id"),
                saleId,
                longField(p, "paymentMethodId"),
                decimal(p, "amount"),
                optionalDecimal(p, "amountReceived"),
                optionalText(p, "reference"),
                optionalText(p, "cardLast4"),
                timestamp(instant(p, "paidAt")),
                timestamp(instant(p, "createdAt")),
                text(p, "createdBy"),
                timestamp(instant(p, "updatedAt")),
                text(p, "updatedBy"),
                intField(p, "version")
            });
        }
        return rows;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
