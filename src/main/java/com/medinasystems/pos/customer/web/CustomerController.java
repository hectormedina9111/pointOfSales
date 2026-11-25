package com.medinasystems.pos.customer.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.customer.application.CustomerService;
import com.medinasystems.pos.customer.application.CustomerView;
import com.medinasystems.pos.customer.domain.CustomerDetails;
import com.medinasystems.pos.customer.domain.CustomerType;
import com.medinasystems.pos.customer.domain.DocumentType;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Customers. Every node lists, reads and creates them; the central node also changes and deactivates them. A store
 * creation is uploaded to the central node by the service.
 */
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    /** {@code search} matches the display name, the document number or the external code. */
    @GetMapping
    public PageResponse<CustomerView> list(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) Boolean active,
                                           @PageableDefault(sort = {"displayName"}) Pageable pageable) {
        return PageResponse.from(customers.list(search, active, pageable));
    }

    @GetMapping("/{id}")
    public CustomerView get(@PathVariable Long id) {
        return customers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerView create(@Valid @RequestBody CustomerRequest request) {
        return customers.create(request.toDetails());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public CustomerView update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customers.update(id, request.toDetails());
    }

    /** DELETE deactivates the customer; the row stays, because documents point to it. */
    @DeleteMapping("/{id}")
    @CentralOnly
    public CustomerView deactivate(@PathVariable Long id) {
        return customers.deactivate(id);
    }

    /**
     * The customer type defaults to PERSON and the active flag to true when omitted. The names that do not apply to the
     * type are ignored; the service and the domain check the rest.
     */
    public record CustomerRequest(@Size(max = 50) String externalCode,
                                  CustomerType customerType,
                                  @NotNull DocumentType documentType,
                                  @Size(max = 20) String documentNumber,
                                  @Size(max = 60) String firstName,
                                  @Size(max = 60) String middleName,
                                  @Size(max = 60) String lastName,
                                  @Size(max = 60) String secondLastName,
                                  @Size(max = 200) String businessName,
                                  @Size(max = 20) String phone,
                                  @Email @Size(max = 150) String email,
                                  @Size(max = 300) String address,
                                  Long priceListId,
                                  Boolean active) {

        CustomerDetails toDetails() {
            return new CustomerDetails(externalCode, customerType == null ? CustomerType.PERSON : customerType,
                    documentType, documentNumber, firstName, middleName, lastName, secondLastName, businessName, phone,
                    email, address, priceListId, active == null || active);
        }
    }
}
