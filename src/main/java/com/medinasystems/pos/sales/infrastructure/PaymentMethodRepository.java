package com.medinasystems.pos.sales.infrastructure;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.sales.domain.PaymentMethod;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    boolean existsByCode(String code);

    Optional<PaymentMethod> findByCode(String code);

    Page<PaymentMethod> findByActive(boolean active, Pageable pageable);
}
