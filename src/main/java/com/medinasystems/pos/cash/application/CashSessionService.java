package com.medinasystems.pos.cash.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.SessionPaymentTotals;
import com.medinasystems.pos.cash.domain.CashMovement;
import com.medinasystems.pos.cash.domain.CashMovementType;
import com.medinasystems.pos.cash.domain.CashRegister;
import com.medinasystems.pos.cash.domain.CashSession;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.cash.infrastructure.CashMovementRepository;
import com.medinasystems.pos.cash.infrastructure.CashRegisterRepository;
import com.medinasystems.pos.cash.infrastructure.CashSessionRepository;
import com.medinasystems.pos.cash.infrastructure.CashSessionSpecifications;
import com.medinasystems.pos.security.ActionAuthorizer;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Cash sessions of a store: opening, deposits and withdrawals, the summary and the closing count. Every operation
 * works on the signed-in user's own session, except the reads, which follow the branches the node can see.
 */
@Service
@Transactional
public class CashSessionService {

    /** Privilege that lets a cashier withdraw cash without a manager's authorization. */
    static final String WITHDRAWAL_PRIVILEGE = "ACTION_CASH_WITHDRAWAL";

    private static final String OPEN_REGISTER_INDEX = "cash_session_open_register_uk";

    private final CashSessionRepository sessions;
    private final CashMovementRepository movements;
    private final CashRegisterRepository registers;
    private final BranchScope scope;
    private final ActionAuthorizer authorizer;
    private final ObjectProvider<SessionPaymentTotals> paymentTotals;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CashSessionService(CashSessionRepository sessions, CashMovementRepository movements,
                              CashRegisterRepository registers, BranchScope scope, ActionAuthorizer authorizer,
                              ObjectProvider<SessionPaymentTotals> paymentTotals, Outbox outbox,
                              ApplicationEventPublisher events, Clock clock) {
        this.sessions = sessions;
        this.movements = movements;
        this.registers = registers;
        this.scope = scope;
        this.authorizer = authorizer;
        this.paymentTotals = paymentTotals;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Opens a session. The register must be visible and active, and neither the register nor the cashier may have
     * another open session. The database indexes enforce the same rule when two requests race.
     */
    public CashSessionDetailView open(CashSessionData.Open data) {
        long userId = currentUserId();
        CashRegister register = registers.findById(data.cashRegisterId())
                .orElseThrow(() -> new ResourceNotFoundException("cash_register", data.cashRegisterId()));
        requireRegisterVisible(register);
        if (!register.isActive()) {
            throw new BusinessRuleException("CASH_REGISTER_INACTIVE", "The cash register is inactive");
        }
        if (sessions.existsByCashRegisterIdAndStatus(register.getId(), CashSessionStatus.OPEN)) {
            throw new BusinessRuleException("CASH_REGISTER_BUSY", "The cash register already has an open session");
        }
        if (sessions.existsByUserIdAndStatus(userId, CashSessionStatus.OPEN)) {
            throw new BusinessRuleException("USER_HAS_OPEN_SESSION", "The cashier already has an open session");
        }
        CashSession session = CashSession.open(register.getId(), userId, data.openingAmount(), clock.instant());
        CashSession saved;
        try {
            saved = sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            throw openConflict(ex);
        }
        events.publishEvent(new AuditEvent("CASH_SESSION_OPENED", "cash_session", saved.getId(), null,
                sessionSnapshot(saved)));
        enqueueSnapshot(saved, register.getBranchId());
        return CashSessionDetailView.from(saved, List.of());
    }

    /** The signed-in user's open session. Answers 404 when the cashier has none. */
    @Transactional(readOnly = true)
    public CashSessionDetailView current() {
        CashSession session = sessions.findByUserIdAndStatus(currentUserId(), CashSessionStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("cash_session", "current"));
        requireSessionVisible(session);
        return detail(session);
    }

    @Transactional(readOnly = true)
    public CashSessionDetailView get(Long id) {
        return detail(findVisible(id));
    }

    /**
     * Sessions the node can see, newest first. A store lists the sessions of the registers of its branch; the central
     * node lists all of them. A store without a branch sees none. Without {@code ACTION_SALES_VIEW_ALL} the list shows
     * only the signed-in user's own sessions, whatever {@code userId} asks for. {@code branchId} narrows the list to one
     * branch; on a store, another branch gives an empty list.
     */
    @Transactional(readOnly = true)
    public Page<CashSessionView> list(CashSessionStatus status, Long cashRegisterId, Long userId, Long branchId,
                                      Instant from, Instant to, Pageable pageable) {
        long current = currentUserId();
        Long owner = authorizer.hasPrivilege(current, ActionAuthorizer.SALES_VIEW_ALL_PRIVILEGE)
                ? userId : Long.valueOf(current);
        Long filterBranch = branchId;
        if (!scope.allBranches()) {
            Optional<Long> node = scope.nodeBranchId();
            if (node.isEmpty() || (branchId != null && !branchId.equals(node.get()))) {
                return Page.empty(pageable);
            }
            filterBranch = node.get();
        }
        List<Long> registerIds = null;
        if (filterBranch != null) {
            registerIds = registers.findByBranchId(filterBranch).stream().map(CashRegister::getId).toList();
            if (registerIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        Page<CashSession> page = sessions.findAll(
                CashSessionSpecifications.matching(registerIds, cashRegisterId, owner, status, from, to), pageable);
        Map<Long, Long> branchByRegister = branchesOf(page.getContent());
        return page.map(session -> CashSessionView.from(session, branchByRegister.get(session.getCashRegisterId())));
    }

    /**
     * Records a deposit or a withdrawal. A withdrawal needs a manager's authorization, unless the cashier holds
     * {@value #WITHDRAWAL_PRIVILEGE}; the authorizing user is stored either way.
     */
    public CashMovementView addMovement(Long sessionId, CashSessionData.Movement data) {
        CashSession session = findVisible(sessionId);
        long userId = currentUserId();
        session.requireOpenBy(userId);
        Long authorizedBy = authorizedBy(data, userId);
        CashMovement movement = session.addMovement(userId, data.type(), data.amount(), data.reason(),
                authorizedBy, clock.instant());
        CashMovement saved = movements.save(movement);
        events.publishEvent(new AuditEvent("CASH_MOVEMENT_ADDED", "cash_movement", saved.getId(), null,
                movementSnapshot(saved)));
        enqueueSnapshot(session, branchOf(session));
        return CashMovementView.from(saved);
    }

    @Transactional(readOnly = true)
    public SessionSummaryView summary(Long sessionId) {
        CashSession session = findVisible(sessionId);
        Map<String, BigDecimal> expected = expected(session);
        BigDecimal total = expected.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SessionSummaryView(session.getId(), session.getStatus(), expected, total);
    }

    /**
     * Closes the session with the count. The expected amounts are computed at this moment, the count is compared with
     * them, and the session becomes CLOSED. Movements are not allowed afterwards.
     */
    public CashSessionDetailView close(Long sessionId, CashSessionData.Close data) {
        CashSession session = findVisible(sessionId);
        long userId = currentUserId();
        session.requireOpenBy(userId);
        Map<String, BigDecimal> counted = countedByMethod(data.counts());
        session.close(userId, expected(session), counted, data.notes(), clock.instant());
        events.publishEvent(new AuditEvent("CASH_SESSION_CLOSED", "cash_session", session.getId(), null,
                closingSnapshot(session)));
        enqueueSnapshot(session, branchOf(session));
        return detail(session);
    }

    /**
     * Queues the full snapshot of the session (the session and all its movements) for the central node. Called after
     * each change, so the latest snapshot always carries everything; the central node keeps the most recent one.
     */
    private void enqueueSnapshot(CashSession session, long branchId) {
        outbox.enqueue(CashSessionDocument.EVENT_TYPE, CashSessionDocument.of(session, branchId,
                movements.findByCashSessionIdOrderByIdAsc(session.getId())));
    }

    private long branchOf(CashSession session) {
        return registers.findById(session.getCashRegisterId())
                .map(CashRegister::getBranchId)
                .orElseThrow(() -> new IllegalStateException("The cash session has no register: " + session.getId()));
    }

    /** The branch of each register of the given sessions, read once per page. */
    private Map<Long, Long> branchesOf(List<CashSession> found) {
        List<Long> registerIds = found.stream().map(CashSession::getCashRegisterId).distinct().toList();
        Map<Long, Long> branches = new LinkedHashMap<>();
        for (CashRegister register : registers.findAllById(registerIds)) {
            branches.put(register.getId(), register.getBranchId());
        }
        return branches;
    }

    /**
     * Who authorized the movement: the cashier for a withdrawal when they hold the privilege, a manager with the
     * credentials otherwise, and nobody for a deposit.
     */
    private Long authorizedBy(CashSessionData.Movement data, long userId) {
        if (data.type() != CashMovementType.WITHDRAWAL) {
            return null;
        }
        if (authorizer.hasPrivilege(userId, WITHDRAWAL_PRIVILEGE)) {
            return userId;
        }
        CashSessionData.Authorization authorization = data.authorization();
        if (authorization == null) {
            throw new BusinessRuleException("AUTHORIZATION_REQUIRED", "A manager must authorize this withdrawal");
        }
        return authorizer.authorize(authorization.username(), authorization.password(), WITHDRAWAL_PRIVILEGE);
    }

    /** Expected amounts of the session, computed from its movements and the sales collections. */
    private Map<String, BigDecimal> expected(CashSession session) {
        return session.expectedByMethod(movements.findByCashSessionIdOrderByIdAsc(session.getId()),
                collected(session.getId()));
    }

    /** Without the sales module there is nothing collected: only the opening float and the movements count. */
    private Map<String, BigDecimal> collected(long sessionId) {
        SessionPaymentTotals totals = paymentTotals.getIfAvailable();
        return totals == null ? Map.of() : totals.collectedByMethod(sessionId);
    }

    /** A method counted twice would make the difference ambiguous, so the count is rejected. */
    private static Map<String, BigDecimal> countedByMethod(List<CashSessionData.Count> counts) {
        Map<String, BigDecimal> counted = new LinkedHashMap<>();
        for (CashSessionData.Count count : counts) {
            if (counted.putIfAbsent(count.paymentMethodCode(), count.amount()) != null) {
                throw new BusinessRuleException("COUNT_DUPLICATED", "Each payment method can be counted only once");
            }
        }
        return counted;
    }

    private CashSessionDetailView detail(CashSession session) {
        return CashSessionDetailView.from(session, movements.findByCashSessionIdOrderByIdAsc(session.getId()));
    }

    /** A session whose register the node cannot see answers 404, as if it did not exist. */
    private CashSession findVisible(Long id) {
        CashSession session = sessions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("cash_session", id));
        requireSessionVisible(session);
        return session;
    }

    private void requireSessionVisible(CashSession session) {
        boolean visible = registers.findById(session.getCashRegisterId())
                .map(register -> scope.canSee(register.getBranchId()))
                .orElse(false);
        if (!visible) {
            throw new ResourceNotFoundException("cash_session", session.getId());
        }
    }

    private void requireRegisterVisible(CashRegister register) {
        if (!scope.canSee(register.getBranchId())) {
            throw new ResourceNotFoundException("cash_register", register.getId());
        }
    }

    /**
     * Two open sessions of the same register are refused by the database; the index name tells which rule was broken.
     * The one open session per cashier is checked by the query in open(), not by an index (see V15).
     * Any other violation is not ours to translate.
     */
    private static RuntimeException openConflict(DataIntegrityViolationException ex) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (cause.contains(OPEN_REGISTER_INDEX)) {
            return new BusinessRuleException("CASH_REGISTER_BUSY", "The cash register already has an open session");
        }
        return ex;
    }

    private static long currentUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        throw new IllegalStateException("The request has no signed-in user");
    }

    private static Map<String, Object> sessionSnapshot(CashSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cashRegisterId", session.getCashRegisterId());
        values.put("userId", session.getUserId());
        values.put("status", session.getStatus().name());
        values.put("openingAmount", session.getOpeningAmount());
        return values;
    }

    private static Map<String, Object> movementSnapshot(CashMovement movement) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cashSessionId", movement.getCashSessionId());
        values.put("type", movement.getType().name());
        values.put("amount", movement.getAmount());
        values.put("authorizedBy", movement.getAuthorizedBy());
        return values;
    }

    private static Map<String, Object> closingSnapshot(CashSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", session.getStatus().name());
        values.put("expectedTotal", session.getExpectedTotal());
        values.put("countedTotal", session.getCountedTotal());
        values.put("difference", session.getDifference());
        return values;
    }
}
