package com.medinasystems.pos.customer.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.domain.Customer;
import com.medinasystems.pos.customer.domain.CustomerDetails;
import com.medinasystems.pos.customer.domain.DocumentType;
import com.medinasystems.pos.customer.infrastructure.CustomerRepository;
import com.medinasystems.pos.customer.infrastructure.CustomerSpecifications;
import com.medinasystems.pos.pricing.PriceListDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessConflictException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Customers. Every node can list, read and create them; only the central node changes or deactivates them. A customer
 * created in a store is also queued for the central node, in the same transaction, so it is never lost.
 *
 * <p>Uniqueness is checked against what this node knows. The central node accepts a document that a store has already
 * registered, because two stores may register the same person while they are offline.
 */
@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final PriceListDirectory priceLists;
    private final NodeProperties node;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;

    public CustomerService(CustomerRepository customers, PriceListDirectory priceLists, NodeProperties node,
                           Outbox outbox, ApplicationEventPublisher events) {
        this.customers = customers;
        this.priceLists = priceLists;
        this.node = node;
        this.outbox = outbox;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<CustomerView> list(String search, Boolean active, Pageable pageable) {
        return customers.findAll(CustomerSpecifications.matching(search, active), pageable).map(CustomerView::from);
    }

    @Transactional(readOnly = true)
    public CustomerView get(Long id) {
        return CustomerView.from(find(id));
    }

    public CustomerView create(CustomerDetails details) {
        requirePriceList(details.priceListId());
        requireUniqueDocument(details, null);
        requireUniqueExternalCode(details.externalCode(), null);
        Customer saved = customers.save(new Customer(details));
        if (node.isStore()) {
            outbox.enqueue(CustomerDocument.EVENT_TYPE, CustomerDocument.of(saved));
        }
        events.publishEvent(AuditEvent.created("CUSTOMER_CREATED", "customer", saved.getId(), snapshot(saved)));
        return CustomerView.from(saved);
    }

    /** Central node only (enforced by the controller). The final consumer cannot be changed. */
    public CustomerView update(Long id, CustomerDetails details) {
        Customer customer = find(id);
        requireMutable(id);
        requirePriceList(details.priceListId());
        requireUniqueDocument(details, id);
        requireUniqueExternalCode(details.externalCode(), id);
        Map<String, Object> before = snapshot(customer);
        customer.update(details);
        events.publishEvent(AuditEvent.updated("CUSTOMER_UPDATED", "customer", id, before, snapshot(customer)));
        return CustomerView.from(customer);
    }

    /** DELETE deactivates the customer. Central node only (enforced by the controller). */
    public CustomerView deactivate(Long id) {
        Customer customer = find(id);
        requireMutable(id);
        Map<String, Object> before = snapshot(customer);
        customer.deactivate();
        events.publishEvent(AuditEvent.updated("CUSTOMER_DEACTIVATED", "customer", id, before, snapshot(customer)));
        return CustomerView.from(customer);
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("customer", id));
    }

    private static void requireMutable(Long id) {
        if (id != null && id == CustomerDirectory.FINAL_CONSUMER_ID) {
            throw new BusinessRuleException("FINAL_CONSUMER_LOCKED",
                    "The final consumer cannot be changed or deactivated");
        }
    }

    private void requirePriceList(Long priceListId) {
        if (priceListId != null && !priceLists.exists(priceListId)) {
            throw new BusinessRuleException("CUSTOMER_PRICE_LIST_NOT_FOUND", "The customer price list does not exist");
        }
    }

    /** Only an active customer with a document counts: the final consumer and inactive customers are not checked. */
    private void requireUniqueDocument(CustomerDetails details, Long id) {
        if (!details.active() || details.documentType() == DocumentType.NONE) {
            return;
        }
        boolean taken = id == null
                ? customers.existsByDocumentTypeAndDocumentNumberAndActiveTrue(details.documentType(),
                        details.documentNumber())
                : customers.existsByDocumentTypeAndDocumentNumberAndActiveTrueAndIdNot(details.documentType(),
                        details.documentNumber(), id);
        if (taken) {
            throw new BusinessConflictException("DUPLICATE_CUSTOMER_DOCUMENT",
                    "An active customer already has this document");
        }
    }

    private void requireUniqueExternalCode(String externalCode, Long id) {
        if (externalCode == null) {
            return;
        }
        boolean taken = id == null
                ? customers.existsByExternalCode(externalCode)
                : customers.existsByExternalCodeAndIdNot(externalCode, id);
        if (taken) {
            throw new BusinessConflictException("DUPLICATE_CUSTOMER_EXTERNAL_CODE",
                    "The customer code is already in use");
        }
    }

    private static Map<String, Object> snapshot(Customer customer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("externalCode", customer.getExternalCode());
        values.put("customerType", customer.getCustomerType().name());
        values.put("documentType", customer.getDocumentType().name());
        values.put("documentNumber", customer.getDocumentNumber());
        values.put("displayName", customer.getDisplayName());
        values.put("phone", customer.getPhone());
        values.put("email", customer.getEmail());
        values.put("address", customer.getAddress());
        values.put("priceListId", customer.getPriceListId());
        values.put("active", customer.isActive());
        return values;
    }
}
