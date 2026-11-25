package com.medinasystems.pos.customer.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.customer.infrastructure.CustomerRepository;

/** Answers {@link CustomerDirectory} from the customer table. */
@Component
public class DefaultCustomerDirectory implements CustomerDirectory {

    private final CustomerRepository customers;

    public DefaultCustomerDirectory(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerInfo> find(long id) {
        return customers.findById(id).map(customer -> new CustomerInfo(customer.getId(), customer.getDisplayName(),
                customer.getDocumentType().name(), customer.getDocumentNumber(), customer.getPriceListId(),
                customer.isActive()));
    }
}
