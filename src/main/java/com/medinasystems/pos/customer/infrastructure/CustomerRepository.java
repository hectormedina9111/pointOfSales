package com.medinasystems.pos.customer.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.medinasystems.pos.customer.domain.Customer;
import com.medinasystems.pos.customer.domain.DocumentType;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    boolean existsByDocumentTypeAndDocumentNumberAndActiveTrue(DocumentType documentType, String documentNumber);

    boolean existsByDocumentTypeAndDocumentNumberAndActiveTrueAndIdNot(DocumentType documentType,
                                                                       String documentNumber, Long id);

    boolean existsByExternalCode(String externalCode);

    boolean existsByExternalCodeAndIdNot(String externalCode, Long id);
}
