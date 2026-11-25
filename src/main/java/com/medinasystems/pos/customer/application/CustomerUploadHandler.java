package com.medinasystems.pos.customer.application;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.customer.domain.CustomerDetails;
import com.medinasystems.pos.customer.domain.CustomerType;
import com.medinasystems.pos.customer.domain.DocumentType;
import com.medinasystems.pos.pricing.PriceListDirectory;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.UploadHandler;
import com.medinasystems.pos.sync.UploadOutcome;
import com.medinasystems.pos.sync.UploadPayload;
import com.medinasystems.pos.sync.UploadPayload.InvalidPayloadException;

import tools.jackson.databind.JsonNode;

/**
 * Central side: inserts a customer created in a store (CUSTOMER_CREATED) with the id the store gave it. The id is inside
 * the store's range, so it cannot collide with a central id. The row is inserted with the explicit id, which JPA cannot
 * do for an identity column, so this is a native insert.
 *
 * <p>The central node checks only completeness and the price list. It does not check the duplicate document: two stores
 * may register the same person while they are offline, and both records are kept.
 */
@Component
@Profile("central")
public class CustomerUploadHandler implements UploadHandler {

    private static final String INSERT_SQL = "INSERT INTO customer (id, external_code, customer_type, document_type,"
            + " document_number, first_name, middle_name, last_name, second_last_name, business_name, phone, email,"
            + " address, price_list_id, active, created_at, created_by, updated_at, updated_by, version)"
            + " OVERRIDING SYSTEM VALUE VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String ID_EXISTS_SQL = "SELECT COUNT(*) FROM customer WHERE id = ?";
    private static final String EXTERNAL_CODE_TAKEN_SQL = "SELECT COUNT(*) FROM customer WHERE external_code = ?";

    private final JdbcTemplate jdbc;
    private final PriceListDirectory priceLists;
    private final StoreIdRanges ranges;

    public CustomerUploadHandler(JdbcTemplate jdbc, PriceListDirectory priceLists, StoreIdRanges ranges) {
        this.jdbc = jdbc;
        this.priceLists = priceLists;
        this.ranges = ranges;
    }

    @Override
    public String eventType() {
        return CustomerDocument.EVENT_TYPE;
    }

    @Override
    public UploadOutcome apply(long storeNodeId, Long storeBranchId, JsonNode payload) {
        Upload upload;
        try {
            upload = Upload.from(payload);
        } catch (InvalidPayloadException | BusinessRuleException ex) {
            return UploadOutcome.rejected("INVALID_PAYLOAD");
        }
        if (!ranges.contains(storeNodeId, upload.id())) {
            return UploadOutcome.rejected("ID_OUT_OF_RANGE");
        }
        // The store sends an item again when it has not seen the answer: a customer that is already here is applied.
        if (exists(ID_EXISTS_SQL, upload.id())) {
            return UploadOutcome.accepted();
        }
        Long priceListId = upload.details().priceListId();
        // The price list may still be on its way: the store sends the customer again later.
        if (priceListId != null && !priceLists.exists(priceListId)) {
            return UploadOutcome.retry("PRICE_LIST_MISSING");
        }
        String externalCode = upload.details().externalCode();
        if (externalCode != null && exists(EXTERNAL_CODE_TAKEN_SQL, externalCode)) {
            return UploadOutcome.rejected("DUPLICATE_CUSTOMER_EXTERNAL_CODE");
        }
        CustomerDetails details = upload.details();
        jdbc.update(INSERT_SQL, upload.id(), details.externalCode(), details.customerType().name(),
                details.documentType().name(), details.documentNumber(), details.firstName(), details.middleName(),
                details.lastName(), details.secondLastName(), details.businessName(), details.phone(),
                details.email(), details.address(), details.priceListId(), details.active(),
                Timestamp.from(upload.createdAt()), upload.createdBy(), Timestamp.from(upload.updatedAt()),
                upload.updatedBy(), upload.version());
        return UploadOutcome.accepted();
    }

    private boolean exists(String sql, Object argument) {
        Long count = jdbc.queryForObject(sql, Long.class, argument);
        return count != null && count > 0;
    }

    /** Every field of a CUSTOMER_CREATED payload, read and checked before anything is written. */
    private record Upload(long id, CustomerDetails details, Instant createdAt, String createdBy, Instant updatedAt,
                          String updatedBy, int version) {

        static Upload from(JsonNode payload) {
            long id = UploadPayload.longField(payload, "id");
            CustomerDetails details = new CustomerDetails(
                    UploadPayload.optionalText(payload, "externalCode"),
                    enumField(payload, "customerType", CustomerType.class),
                    enumField(payload, "documentType", DocumentType.class),
                    UploadPayload.optionalText(payload, "documentNumber"),
                    UploadPayload.optionalText(payload, "firstName"),
                    UploadPayload.optionalText(payload, "middleName"),
                    UploadPayload.optionalText(payload, "lastName"),
                    UploadPayload.optionalText(payload, "secondLastName"),
                    UploadPayload.optionalText(payload, "businessName"),
                    UploadPayload.optionalText(payload, "phone"),
                    UploadPayload.optionalText(payload, "email"),
                    UploadPayload.optionalText(payload, "address"),
                    UploadPayload.optionalLong(payload, "priceListId"),
                    UploadPayload.bool(payload, "active"));
            return new Upload(id, details, UploadPayload.instant(payload, "createdAt"),
                    UploadPayload.text(payload, "createdBy"), UploadPayload.instant(payload, "updatedAt"),
                    UploadPayload.text(payload, "updatedBy"), UploadPayload.intField(payload, "version"));
        }

        private static <E extends Enum<E>> E enumField(JsonNode payload, String name, Class<E> type) {
            String value = UploadPayload.text(payload, name);
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ex) {
                throw new InvalidPayloadException(name);
            }
        }
    }
}
