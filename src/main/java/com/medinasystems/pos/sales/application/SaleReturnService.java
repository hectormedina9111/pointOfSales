package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.medinasystems.pos.cash.OpenSessionInfo;
import com.medinasystems.pos.sales.SaleReturned;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.sales.domain.CalculatedReturnLine;
import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.PaymentDraft;
import com.medinasystems.pos.sales.domain.RefundMethod;
import com.medinasystems.pos.sales.domain.RefundRules;
import com.medinasystems.pos.sales.domain.ReceiptCounter;
import com.medinasystems.pos.sales.domain.ReturnCalculator;
import com.medinasystems.pos.sales.domain.ReturnClaimStatus;
import com.medinasystems.pos.sales.domain.ReturnLineRequest;
import com.medinasystems.pos.sales.domain.ReturnOrigin;
import com.medinasystems.pos.sales.domain.ReturnSourceLine;
import com.medinasystems.pos.sales.domain.ReturnTotals;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleLine;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.CreditNoteRepository;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;
import com.medinasystems.pos.sales.infrastructure.SaleLineRepository;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;
import com.medinasystems.pos.sales.infrastructure.SaleReturnClaimRepository;
import com.medinasystems.pos.security.ActionAuthorizer;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessNotFoundException;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.sync.CentralUnavailableException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Returns of sales. A return refunds some or all lines of a paid sale. On the day of the sale it refunds now, in cash or
 * with the original method; on a later day it issues a credit note. The return is authorized by the signed-in user when
 * they hold {@code ACTION_SALE_RETURN}, or by a manager with the credentials.
 *
 * <p>The sale is one of this node's, or a sale of another branch: then the branch must accept such returns, the central
 * node gives the sale's returnable view, and the quantities are held there before the return is written. Returns of this
 * node's own sales also hold their quantities when the central node is reachable, and go on offline when it is not.
 */
@Service
@Transactional
public class SaleReturnService {

    /** Privilege that lets a user return sales without a manager's authorization. */
    static final String RETURN_PRIVILEGE = "ACTION_SALE_RETURN";

    private static final Logger log = LoggerFactory.getLogger(SaleReturnService.class);

    /** Claim states that hold a quantity: a held quantity is not returnable by another return. */
    private static final Set<ReturnClaimStatus> HOLDING = Set.of(ReturnClaimStatus.RESERVED,
            ReturnClaimStatus.CONFIRMED);

    private final SaleService service;
    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final PaymentRepository payments;
    private final CreditNoteRepository creditNotes;
    private final CompanyDirectory companies;
    private final BranchDirectory branches;
    private final SaleReturnClaimRepository claims;
    private final CentralReturns central;
    private final ActionAuthorizer authorizer;
    private final BranchScope scope;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SaleReturnService(SaleService service, SaleRepository sales, SaleLineRepository lines,
                             PaymentRepository payments, CreditNoteRepository creditNotes, CompanyDirectory companies,
                             BranchDirectory branches, SaleReturnClaimRepository claims, CentralReturns central,
                             ActionAuthorizer authorizer, BranchScope scope, Outbox outbox,
                             ApplicationEventPublisher events, Clock clock) {
        this.service = service;
        this.sales = sales;
        this.lines = lines;
        this.payments = payments;
        this.creditNotes = creditNotes;
        this.companies = companies;
        this.branches = branches;
        this.claims = claims;
        this.central = central;
        this.authorizer = authorizer;
        this.scope = scope;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    /**
     * What can be returned from a sale, and how it is refunded. A sale of this node is read locally; any other sale is read
     * from the central node, when this branch accepts returns of other branches.
     */
    @Transactional(readOnly = true)
    public ReturnableView returnable(long saleId) {
        Optional<Sale> own = ownSale(saleId);
        if (own.isPresent()) {
            return localView(own.get());
        }
        return foreignView(saleId);
    }

    /**
     * The returnable view of a sale found by its receipt number: this node's sale first, then a sale of another branch when
     * this branch accepts such returns.
     */
    @Transactional(readOnly = true)
    public ReturnableView lookup(String receiptNumber) {
        String number = receiptNumber == null ? "" : receiptNumber.trim();
        Optional<Sale> own = sales.findByReceiptNumberAndType(number, SaleType.SALE)
                .filter(sale -> scope.canSee(sale.getBranchId()));
        if (own.isPresent()) {
            return localView(own.get());
        }
        requireForeignReturns();
        return markForeign(central.byReceipt(number));
    }

    /** The amounts a return of these lines would refund. Nothing is stored and no authorization is needed. */
    @Transactional(readOnly = true)
    public ReturnQuoteView quote(long saleId, List<SaleData.ReturnLine> requested) {
        Optional<Sale> own = ownSale(saleId);
        ReturnTotals totals;
        if (own.isPresent()) {
            Sale sale = own.get();
            totals = ReturnCalculator.calculate(sale.isReturnable(), sourceLines(sale), requests(requested),
                    refundedByLine(sale.getId()));
        } else {
            ReturnableView view = foreignView(saleId);
            totals = ReturnCalculator.calculate(true, view.sourceLines(), requests(requested),
                    refundedByLine(view));
        }
        return ReturnQuoteView.from(totals);
    }

    /**
     * Creates the return. An idempotency key already used returns its return without creating anything. Otherwise the
     * sale is calculated and its refunds checked, the authorization is checked, the quantities are held at the central
     * node, and the return and the credit note (when one is issued) are written. The return is queued for upload.
     */
    public SaleResult create(SaleData.Return data) {
        long userId = SaleService.currentUserId();
        Optional<Sale> done = sales.findByIdempotencyKey(data.idempotencyKey());
        if (done.isPresent()) {
            return new SaleResult(service.view(done.get()), false);
        }
        Optional<Sale> locked = sales.findByIdForUpdate(data.saleId()).filter(found -> scope.canSee(found.getBranchId()));
        done = sales.findByIdempotencyKey(data.idempotencyKey());
        if (done.isPresent()) {
            return new SaleResult(service.view(done.get()), false);
        }

        OpenSessionInfo session = service.currentSession(userId);
        ReceiptCounter counter = service.lockCounter(session.cashRegisterId(), ReceiptCounter.RETURN);
        ReturnOrigin origin;
        List<ReturnSourceLine> source;
        Map<Long, BigDecimal> refundedLines;
        Map<String, BigDecimal> refundedMethods;
        Map<String, BigDecimal> paid;
        boolean sameDay;
        boolean returnable;
        if (locked.isPresent()) {
            Sale sale = locked.get();
            origin = ReturnOrigin.of(sale);
            source = sourceLines(sale);
            refundedLines = refundedByLine(sale.getId());
            refundedMethods = refundedByMethod(sale.getId());
            paid = paidByMethod(sale);
            sameDay = sameDay(sale.getPaidAt(), zone());
            returnable = sale.isReturnable();
        } else {
            ReturnableView view = foreignView(data.saleId());
            origin = view.origin();
            source = view.sourceLines();
            refundedLines = refundedByLine(view);
            refundedMethods = refundedByMethod(view);
            paid = paidByMethod(view);
            sameDay = view.sameDay();
            returnable = true;
        }
        ReturnTotals totals = ReturnCalculator.calculate(returnable, source, requests(data.lines()), refundedLines);
        List<PaymentDraft> refunds = service.paymentDrafts(data.refunds());
        RefundMethod method = RefundRules.check(totals.total(), sameDay, refunds, paid, refundedMethods);
        Long authorizedBy = service.authorizedBy(true, RETURN_PRIVILEGE, data.authorization(), userId,
                "RETURN_AUTHORIZATION_REQUIRED", "A manager must authorize this return");

        holdAtCentral(data, locked.isPresent());
        String receiptNumber = session.receiptPrefix() + "-D" + String.format(Locale.ROOT, "%06d", counter.next());
        Sale ret = Sale.refund(SaleService.context(session, userId), sales.nextInternalNumber(), origin, totals,
                data.reason(), method, authorizedBy, refunds, receiptNumber, data.idempotencyKey(), clock.instant());
        locked.ifPresent(sale -> sale.registerReturn(returnedByLine(totals)));
        locked.ifPresent(sale -> markReturnedSerials(sale, totals));
        Sale saved = sales.saveAndFlush(ret);

        CreditNote note = null;
        if (method == RefundMethod.CREDIT_NOTE) {
            ReceiptCounter noteCounter = service.lockCounter(session.cashRegisterId(), ReceiptCounter.CREDIT_NOTE);
            String number = session.receiptPrefix() + "-NC" + String.format(Locale.ROOT, "%06d", noteCounter.next());
            note = creditNotes.saveAndFlush(new CreditNote(number, origin.customerId(), saved.getId(),
                    session.branchId(), totals.total(), clock.instant()));
        }
        events.publishEvent(AuditEvent.created("SALE_RETURNED", "sale", saved.getId(), returnedSnapshot(saved, note)));
        outbox.enqueue(SaleDocument.EVENT_TYPE, SaleDocument.of(saved, note));
        events.publishEvent(new SaleReturned(saved.getId(), saved.getBranchId(), userId, returnedLines(saved)));
        return new SaleResult(service.view(saved), true);
    }

    /**
     * Holds the quantities of the return at the central node. A sale of another branch must be held: when the central node
     * cannot be reached, the return is refused. An own sale is held when the central node is reachable and knows the sale;
     * otherwise the return goes on offline, and the central node accepts it later (see {@code SaleUploadHandler}).
     *
     * A held quantity is given back if the transaction of the return does not commit.
     */
    private void holdAtCentral(SaleData.Return data, boolean own) {
        try {
            central.reserve(data.saleId(), data.idempotencyKey(), requests(data.lines()));
        } catch (CentralUnavailableException ex) {
            if (!own) {
                throw ex;
            }
            log.info("Return {} was made offline: its quantities are not held at the central node", data.idempotencyKey());
            return;
        } catch (BusinessNotFoundException ex) {
            if (!own) {
                throw ex;
            }
            log.info("The central node does not have the sale yet: its return {} is not held", data.idempotencyKey());
            return;
        }
        releaseIfRolledBack(data.idempotencyKey());
    }

    /** Gives the held quantities back when the transaction of the return does not commit. */
    private void releaseIfRolledBack(String claimKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    central.release(claimKey);
                }
            }
        });
    }

    /** The view of a sale of this node, with the quantities held by other claims. */
    private ReturnableView localView(Sale sale) {
        if (!sale.isReturnable()) {
            throw new BusinessRuleException("RETURN_NOT_ALLOWED", "Only a paid sale can be returned");
        }
        CustomerInfo customer = service.findCustomer(sale.getCustomerId());
        return ReturnableView.of(sale, sameDay(sale.getPaidAt(), zone()), customer.displayName(),
                branchName(sale.getBranchId()), false, refundedByLine(sale.getId()), refundedByMethod(sale.getId()),
                held(sale.getId()));
    }

    /** The view of a sale of another branch, from the central node. The store decides the same-day rule itself. */
    private ReturnableView foreignView(long saleId) {
        requireForeignReturns();
        return markForeign(central.byId(saleId));
    }

    private ReturnableView markForeign(ReturnableView central) {
        return central.withSameDay(sameDay(central.paidAt(), zone()), true);
    }

    /** Foreign returns are allowed only when the branch of this store accepts them (set by the central node). */
    private void requireForeignReturns() {
        boolean accepted = scope.nodeBranchId().flatMap(branches::info).map(BranchInfo::acceptsForeignReturns)
                .orElse(false);
        if (!accepted) {
            throw new BusinessRuleException("FOREIGN_RETURNS_DISABLED",
                    "This branch does not accept returns of sales made in other branches");
        }
    }

    /** This node's sale with this id, when the node can see it. */
    private Optional<Sale> ownSale(long saleId) {
        return sales.findById(saleId).filter(sale -> scope.canSee(sale.getBranchId()));
    }

    private static List<ReturnLineRequest> requests(List<SaleData.ReturnLine> requested) {
        if (requested == null) {
            return List.of();
        }
        return requested.stream()
                .map(line -> new ReturnLineRequest(line.saleLineId(), line.quantity(), line.serialNumbers())).toList();
    }

    /** Marks the serial numbers that this return brings back as returned on the lines of the sale. */
    private static void markReturnedSerials(Sale sale, ReturnTotals totals) {
        Map<Long, SaleLine> byId = new HashMap<>();
        sale.getLines().forEach(line -> byId.put(line.getId(), line));
        for (CalculatedReturnLine line : totals.lines()) {
            if (!line.serialNumbers().isEmpty()) {
                byId.get(line.original().id()).markSerialsReturned(line.serialNumbers());
            }
        }
    }

    private static Map<Long, BigDecimal> returnedByLine(ReturnTotals totals) {
        Map<Long, BigDecimal> returned = new HashMap<>();
        totals.lines().forEach(line -> returned.put(line.original().id(), line.quantity()));
        return returned;
    }

    /** The lines of a sale of this node, with the quantity that other claims hold on each. */
    private List<ReturnSourceLine> sourceLines(Sale sale) {
        Map<Long, BigDecimal> held = held(sale.getId());
        return sale.getLines().stream()
                .map(line -> ReturnSourceLine.of(line, held.getOrDefault(line.getId(), BigDecimal.ZERO)))
                .toList();
    }

    /** The quantity held per line of a sale by claims whose return is not in this node's documents. */
    private Map<Long, BigDecimal> held(long saleId) {
        Map<Long, BigDecimal> held = new HashMap<>();
        for (Object[] row : claims.sumHeldByLine(saleId, HOLDING, SaleType.RETURN)) {
            held.put((Long) row[0], (BigDecimal) row[1]);
        }
        return held;
    }

    private Map<Long, BigDecimal> refundedByLine(long saleId) {
        return ReturnRefunds.byLine(lines, saleId);
    }

    private Map<String, BigDecimal> refundedByMethod(long saleId) {
        return ReturnRefunds.byMethod(payments, saleId);
    }

    private static Map<Long, BigDecimal> refundedByLine(ReturnableView view) {
        Map<Long, BigDecimal> refunded = new HashMap<>();
        view.lines().forEach(line -> refunded.put(line.saleLineId(), line.refundedAmount()));
        return refunded;
    }

    private static Map<String, BigDecimal> refundedByMethod(ReturnableView view) {
        Map<String, BigDecimal> refunded = new HashMap<>();
        view.payments().forEach(method -> refunded.put(method.paymentMethodCode(), method.refunded()));
        return refunded;
    }

    private static Map<String, BigDecimal> paidByMethod(ReturnableView view) {
        Map<String, BigDecimal> paid = new HashMap<>();
        view.payments().forEach(method -> paid.put(method.paymentMethodCode(), method.paid()));
        return paid;
    }

    private static Map<String, BigDecimal> paidByMethod(Sale sale) {
        Map<String, BigDecimal> paid = new HashMap<>();
        for (Payment payment : sale.getPayments()) {
            paid.merge(payment.getPaymentMethod().getCode(), payment.getAmount(), BigDecimal::add);
        }
        return paid;
    }

    /** The name of a branch for the screens, or null when the branch is unknown. */
    private String branchName(Long branchId) {
        return branches.info(branchId).map(BranchInfo::name).orElse(null);
    }

    private ZoneId zone() {
        CompanyInfo company = companies.find().orElseThrow(() -> new BusinessRuleException(
                "COMPANY_NOT_CONFIGURED", "Configure the company before returning sales"));
        return ZoneId.of(company.timeZone());
    }

    /** True when the instant falls on the same calendar day as now, in the zone. */
    private boolean sameDay(Instant paidAt, ZoneId zone) {
        return paidAt.atZone(zone).toLocalDate().equals(clock.instant().atZone(zone).toLocalDate());
    }

    /** The lines of a return document, with their ids, for the modules that move stock back. */
    private static List<SaleReturned.Line> returnedLines(Sale ret) {
        return ret.getLines().stream()
                .map(line -> new SaleReturned.Line(line.getId(), line.getProductId(), line.getQuantity()))
                .toList();
    }

    private static Map<String, Object> returnedSnapshot(Sale ret, CreditNote note) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("receiptNumber", ret.getReceiptNumber());
        values.put("originalSaleId", ret.getOriginalSaleId());
        values.put("originalReceiptNumber", ret.getOriginalReceiptNumber());
        values.put("refundMethod", ret.getRefundMethod().name());
        values.put("total", ret.getTotal());
        values.put("returnAuthorizedBy", ret.getReturnAuthorizedBy());
        values.put("creditNoteNumber", note == null ? null : note.getNumber());
        return values;
    }
}
