package com.medinasystems.pos.sales.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sales.domain.PaymentMethod;
import com.medinasystems.pos.sales.infrastructure.PaymentMethodRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Payment methods. Their codes are created by {@link PaymentMethodSeeder}; here only the rest can change. */
@Service
@Transactional
public class PaymentMethodService {

    private final PaymentMethodRepository methods;
    private final ApplicationEventPublisher events;

    public PaymentMethodService(PaymentMethodRepository methods, ApplicationEventPublisher events) {
        this.methods = methods;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<PaymentMethodView> list(Boolean active, Pageable pageable) {
        Page<PaymentMethod> page = active == null
                ? methods.findAll(pageable)
                : methods.findByActive(active, pageable);
        return page.map(PaymentMethodView::from);
    }

    public PaymentMethodView update(Long id, PaymentMethodData data) {
        PaymentMethod method = find(id);
        Map<String, Object> before = snapshot(method);
        method.update(data.name(), data.requiresReference(), data.allowsChange(), data.active());
        events.publishEvent(AuditEvent.updated("PAYMENT_METHOD_UPDATED", "payment_method", id, before,
                snapshot(method)));
        return PaymentMethodView.from(method);
    }

    private PaymentMethod find(Long id) {
        return methods.findById(id).orElseThrow(() -> new ResourceNotFoundException("payment_method", id));
    }

    private static Map<String, Object> snapshot(PaymentMethod method) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", method.getCode());
        values.put("name", method.getName());
        values.put("requiresReference", method.isRequiresReference());
        values.put("allowsChange", method.isAllowsChange());
        values.put("active", method.isActive());
        return values;
    }
}
