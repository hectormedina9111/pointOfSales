package com.medinasystems.pos.cash.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.domain.CashRegister;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.cash.infrastructure.CashRegisterRepository;
import com.medinasystems.pos.cash.infrastructure.CashRegisterSpecifications;
import com.medinasystems.pos.cash.infrastructure.CashSessionRepository;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Cash registers of each branch. The central node creates and changes them; every node lists only the
 * branches it can see (the central node all of them, a store only its own).
 */
@Service
@Transactional
public class CashRegisterService {

    private final CashRegisterRepository registers;
    private final CashSessionRepository sessions;
    private final BranchDirectory branches;
    private final BranchScope scope;
    private final ApplicationEventPublisher events;

    public CashRegisterService(CashRegisterRepository registers, CashSessionRepository sessions,
                               BranchDirectory branches, BranchScope scope, ApplicationEventPublisher events) {
        this.registers = registers;
        this.sessions = sessions;
        this.branches = branches;
        this.scope = scope;
        this.events = events;
    }

    /**
     * A branch the node cannot see gives an empty page, the same as a branch with no registers. A store
     * without an assigned branch sees nothing.
     */
    @Transactional(readOnly = true)
    public Page<CashRegisterView> list(Long branchId, Boolean active, Pageable pageable) {
        if (branchId != null && !scope.canSee(branchId)) {
            return Page.empty(pageable);
        }
        Long visibleBranch = scope.allBranches() ? branchId : scope.nodeBranchId().orElse(null);
        if (!scope.allBranches() && visibleBranch == null) {
            return Page.empty(pageable);
        }
        return registers.findAll(CashRegisterSpecifications.matching(visibleBranch, active), pageable)
                .map(CashRegisterView::from);
    }

    /** A register in a branch this node cannot see answers 404, as if it did not exist. */
    @Transactional(readOnly = true)
    public CashRegisterView get(Long id) {
        CashRegister register = find(id);
        if (!scope.canSee(register.getBranchId())) {
            throw new ResourceNotFoundException("cash_register", id);
        }
        return CashRegisterView.from(register);
    }

    public CashRegisterView create(CashRegisterData data) {
        requireBranch(data.branchId());
        requireUniqueCode(data.branchId(), data.code(), null);
        requireUniqueReceiptPrefix(data.receiptPrefix(), null);
        CashRegister register = new CashRegister(data.branchId(), data.code(), data.name(), data.receiptPrefix(),
                data.active());
        CashRegister saved = registers.save(register);
        events.publishEvent(AuditEvent.created("CASH_REGISTER_CREATED", "cash_register", saved.getId(),
                snapshot(saved)));
        return CashRegisterView.from(saved);
    }

    public CashRegisterView update(Long id, CashRegisterData data) {
        CashRegister register = find(id);
        if (!Objects.equals(register.getBranchId(), data.branchId())) {
            requireBranch(data.branchId());
            // An open session belongs to the register's branch: moving the register would split its cash.
            if (sessions.existsByCashRegisterIdAndStatus(id, CashSessionStatus.OPEN)) {
                throw new BusinessRuleException("CASH_REGISTER_HAS_OPEN_SESSION",
                        "The branch of a cash register with an open session cannot be changed");
            }
        }
        requireUniqueCode(data.branchId(), data.code(), id);
        requireUniqueReceiptPrefix(data.receiptPrefix(), id);
        Map<String, Object> before = snapshot(register);
        register.update(data.branchId(), data.code(), data.name(), data.receiptPrefix(), data.active());
        events.publishEvent(AuditEvent.updated("CASH_REGISTER_UPDATED", "cash_register", id, before,
                snapshot(register)));
        return CashRegisterView.from(register);
    }

    private CashRegister find(Long id) {
        return registers.findById(id).orElseThrow(() -> new ResourceNotFoundException("cash_register", id));
    }

    private void requireBranch(Long branchId) {
        if (!branches.exists(branchId)) {
            throw new BusinessRuleException("BRANCH_NOT_FOUND", "One or more branch ids do not exist");
        }
    }

    private void requireUniqueCode(Long branchId, String code, Long id) {
        boolean taken = id == null
                ? registers.existsByBranchIdAndCode(branchId, code)
                : registers.existsByBranchIdAndCodeAndIdNot(branchId, code, id);
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_CASH_REGISTER_CODE",
                    "The cash register code is already in use in this branch");
        }
    }

    /**
     * The receipt prefix is unique across all registers: two registers with the same prefix would print the same receipt
     * numbers. The database has the same constraint (V15); this check gives the clear error first.
     */
    private void requireUniqueReceiptPrefix(String receiptPrefix, Long id) {
        boolean taken = id == null
                ? registers.existsByReceiptPrefix(receiptPrefix)
                : registers.existsByReceiptPrefixAndIdNot(receiptPrefix, id);
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_RECEIPT_PREFIX",
                    "The receipt prefix is already used by another cash register");
        }
    }

    private static Map<String, Object> snapshot(CashRegister register) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("branchId", register.getBranchId());
        values.put("code", register.getCode());
        values.put("name", register.getName());
        values.put("receiptPrefix", register.getReceiptPrefix());
        values.put("active", register.isActive());
        return values;
    }
}
