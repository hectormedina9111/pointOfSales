package com.medinasystems.pos.sales.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.sales.domain.PaymentMethod;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    boolean existsByCode(String code);

    Page<PaymentMethod> findByActive(boolean active, Pageable pageable);
}
