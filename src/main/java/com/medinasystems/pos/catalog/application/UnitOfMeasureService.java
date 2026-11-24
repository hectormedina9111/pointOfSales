package com.medinasystems.pos.catalog.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.domain.UnitOfMeasure;
import com.medinasystems.pos.catalog.infrastructure.UnitOfMeasureRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Units of measure. The code is unique; a unit is deactivated, never deleted. */
@Service
@Transactional
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository units;
    private final ApplicationEventPublisher events;

    public UnitOfMeasureService(UnitOfMeasureRepository units, ApplicationEventPublisher events) {
        this.units = units;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<UnitOfMeasureView> list(Pageable pageable) {
        return units.findAll(pageable).map(UnitOfMeasureView::from);
    }

    @Transactional(readOnly = true)
    public UnitOfMeasureView get(Long id) {
        return UnitOfMeasureView.from(find(id));
    }

    public UnitOfMeasureView create(UnitOfMeasureData data) {
        if (units.existsByCode(data.code())) {
            throw duplicatedCode();
        }
        UnitOfMeasure unit = new UnitOfMeasure(data.code(), data.name(), data.allowsDecimals(), data.active());
        UnitOfMeasure saved = units.save(unit);
        events.publishEvent(AuditEvent.created("UNIT_CREATED", "unit_of_measure", saved.getId(), snapshot(saved)));
        return UnitOfMeasureView.from(saved);
    }

    public UnitOfMeasureView update(Long id, UnitOfMeasureData data) {
        UnitOfMeasure unit = find(id);
        if (units.existsByCodeAndIdNot(data.code(), id)) {
            throw duplicatedCode();
        }
        Map<String, Object> before = snapshot(unit);
        unit.update(data.code(), data.name(), data.allowsDecimals(), data.active());
        events.publishEvent(AuditEvent.updated("UNIT_UPDATED", "unit_of_measure", id, before, snapshot(unit)));
        return UnitOfMeasureView.from(unit);
    }

    private UnitOfMeasure find(Long id) {
        return units.findById(id).orElseThrow(() -> new ResourceNotFoundException("unit of measure", id));
    }

    private static BusinessRuleException duplicatedCode() {
        return new BusinessRuleException("DUPLICATE_UNIT_CODE", "The unit of measure code is already in use");
    }

    private static Map<String, Object> snapshot(UnitOfMeasure unit) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", unit.getCode());
        values.put("name", unit.getName());
        values.put("allowsDecimals", unit.isAllowsDecimals());
        values.put("active", unit.isActive());
        return values;
    }
}
