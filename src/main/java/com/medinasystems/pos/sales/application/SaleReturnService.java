package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.OpenSessionInfo;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.Payment;
import com.medinasystems.pos.sales.domain.PaymentDraft;
import com.medinasystems.pos.sales.domain.RefundMethod;
import com.medinasystems.pos.sales.domain.RefundRules;
import com.medinasystems.pos.sales.domain.ReceiptCounter;
import com.medinasystems.pos.sales.domain.ReturnCalculator;
import com.medinasystems.pos.sales.domain.ReturnClaimStatus;
import com.medinasystems.pos.sales.domain.ReturnLineRequest;
import com.medinasystems.pos.sales.domain.ReturnSourceLine;
import com.medinasystems.pos.sales.domain.ReturnTotals;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.CreditNoteRepository;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;
import com.medinasystems.pos.sales.infrastructure.SaleReturnClaimRepository;
import com.medinasystems.pos.sales.infrastructure.SaleLineRepository;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;
import com.medinasystems.pos.security.ActionAuthorizer;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.sync.Outbox;

/**
 * Returns of the sales of a store. A return refunds some or all lines of a paid sale. On the day of the sale it refunds now,
 * in cash or with the original method; on a later day it issues a credit note. The return is authorized by the signed-in
 * user when they hold {@code ACTION_SALE_RETURN}, or by a manager with the credentials. The original sale is locked while
 * the return is written, so two returns of one sale cannot both take the same quantity. Only sales of the node's branch
 * can be returned here.
 */
@Service
@Transactional
public class SaleReturnService {

    /** Privilege that lets a user return sales without a manager's authorization. */
    static final String RETURN_PRIVILEGE = "ACTION_SALE_RETURN";

    private final SaleService service;
    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final PaymentRepository payments;
    private final CreditNoteRepository creditNotes;
    private final CompanyDirectory companies;
    private final BranchDirectory branches;
    private final SaleReturnClaimRepository claims;
    private final ActionAuthorizer authorizer;
    private final BranchScope scope;
    private final Outbox outbox;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Claim states that hold a quantity: a held quantity is not returnable by another return. */
    private static final Set<ReturnClaimStatus> HOLDING = Set.of(ReturnClaimStatus.RESERVED,
            ReturnClaimStatus.CONFIRMED);

    public SaleReturnService(SaleService service, SaleRepository sales, SaleLineRepository lines,
                             PaymentRepository payments, CreditNoteRepository creditNotes, CompanyDirectory companies,
                             BranchDirectory branches, SaleReturnClaimRepository claims, ActionAuthorizer authorizer,
                             BranchScope scope, Outbox outbox, ApplicationEventPublisher events, Clock clock) {
        this.service = service;
        this.sales = sales;
        this.lines = lines;
        this.payments = payments;
        this.creditNotes = creditNotes;
        this.companies = companies;
        this.branches = branches;
        this.claims = claims;
        this.authorizer = authorizer;
        this.scope = scope;
        this.outbox = outbox;
        this.events = events;
        this.clock = clock;
    }

    /** What can be returned from a sale, and how it is refunded. A sale that cannot be returned answers RETURN_NOT_ALLOWED. */
    @Transactional(readOnly = true)
    public ReturnableView returnable(long saleId) {
        Sale sale = ownSale(service.findVisible(saleId));
        if (!sale.isReturnable()) {
            throw new BusinessRuleException("RETURN_NOT_ALLOWED", "Only a paid sale can be returned");
        }
        CustomerInfo customer = service.findCustomer(sale.getCustomerId());
        return ReturnableView.of(sale, sameDay(sale, zone()), customer.displayName(), branchName(sale.getBranchId()),
                false, refundedByLine(sale.getId()), refundedByMethod(sale.getId()), held(sale.getId()));
    }

    /** The amounts a return of these lines would refund. Nothing is stored and no authorization is needed. */
    @Transactional(readOnly = true)
    public ReturnQuoteView quote(long saleId, List<SaleData.ReturnLine> requested) {
        Sale sale = ownSale(service.findVisible(saleId));
        ReturnTotals totals = ReturnCalculator.calculate(sale.isReturnable(), sourceLines(sale), requests(requested),
                refundedByLine(sale.getId()));
        return ReturnQuoteView.from(totals);
    }

    /**
     * Creates the return. An idempotency key already used returns its return without creating anything. Otherwise the
     * sale is locked, the return is calculated and its refunds checked, the authorization is checked, and the return and
     * the credit note (when one is issued) are written with their receipt numbers. The return is queued for upload.
     */
    public SaleResult create(SaleData.Return data) {
        long userId = SaleService.currentUserId();
        Optional<Sale> done = sales.findByIdempotencyKey(data.idempotencyKey());
        if (done.isPresent()) {
            return new SaleResult(service.view(done.get()), false);
        }
        Sale sale = sales.findByIdForUpdate(data.saleId()).filter(found -> scope.canSee(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("sale", data.saleId()));
        ownSale(sale);
        done = sales.findByIdempotencyKey(data.idempotencyKey());
        if (done.isPresent()) {
            return new SaleResult(service.view(done.get()), false);
        }

        OpenSessionInfo session = service.currentSession(userId);
        ReceiptCounter counter = service.lockCounter(session.cashRegisterId(), ReceiptCounter.RETURN);
        boolean sameDay = sameDay(sale, zone());
        ReturnTotals totals = ReturnCalculator.calculate(sale.isReturnable(), sourceLines(sale),
                requests(data.lines()), refundedByLine(sale.getId()));
        List<PaymentDraft> refunds = service.paymentDrafts(data.refunds());
        RefundMethod method = RefundRules.check(totals.total(), sameDay, refunds, paidByMethod(sale),
                refundedByMethod(sale.getId()));
        Long authorizedBy = service.authorizedBy(true, RETURN_PRIVILEGE, data.authorization(), userId,
                "RETURN_AUTHORIZATION_REQUIRED", "A manager must authorize this return");

        String receiptNumber = session.receiptPrefix() + "-D" + String.format(Locale.ROOT, "%06d", counter.next());
        Sale ret = Sale.refund(SaleService.context(session, userId), sales.nextInternalNumber(), sale, totals,
                data.reason(), method, authorizedBy, refunds, receiptNumber, data.idempotencyKey(), clock.instant());
        sale.registerReturn(returnedByLine(totals));
        Sale saved = sales.saveAndFlush(ret);

        CreditNote note = null;
        if (method == RefundMethod.CREDIT_NOTE) {
            ReceiptCounter noteCounter = service.lockCounter(session.cashRegisterId(), ReceiptCounter.CREDIT_NOTE);
            String number = session.receiptPrefix() + "-NC" + String.format(Locale.ROOT, "%06d", noteCounter.next());
            note = creditNotes.saveAndFlush(new CreditNote(number, sale.getCustomerId(), saved.getId(),
                    session.branchId(), totals.total(), clock.instant()));
        }
        events.publishEvent(AuditEvent.created("SALE_RETURNED", "sale", saved.getId(), returnedSnapshot(saved, note)));
        outbox.enqueue(SaleDocument.EVENT_TYPE, SaleDocument.of(saved, note));
        return new SaleResult(service.view(saved), true);
    }

    private static List<ReturnLineRequest> requests(List<SaleData.ReturnLine> requested) {
        if (requested == null) {
            return List.of();
        }
        return requested.stream().map(line -> new ReturnLineRequest(line.saleLineId(), line.quantity())).toList();
    }

    private static Map<Long, BigDecimal> returnedByLine(ReturnTotals totals) {
        Map<Long, BigDecimal> returned = new HashMap<>();
        totals.lines().forEach(line -> returned.put(line.original().id(), line.quantity()));
        return returned;
    }

    /** A sale of another branch cannot be returned by this node yet (step 3 adds it). */
    private Sale ownSale(Sale sale) {
        if (scope.nodeBranchId().filter(branch -> branch.equals(sale.getBranchId())).isEmpty()) {
            throw new BusinessRuleException("FOREIGN_SALE_RETURN", "This sale belongs to another branch");
        }
        return sale;
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

    /** The name of a branch for the screens, or null when the branch is unknown. */
    private String branchName(Long branchId) {
        return branches.info(branchId).map(BranchInfo::name).orElse(null);
    }

    private ZoneId zone() {
        CompanyInfo company = companies.find().orElseThrow(() -> new BusinessRuleException(
                "COMPANY_NOT_CONFIGURED", "Configure the company before returning sales"));
        return ZoneId.of(company.timeZone());
    }

    /** True when the return is on the same calendar day as the sale, in the company's time zone. */
    private boolean sameDay(Sale sale, ZoneId zone) {
        return sale.getPaidAt().atZone(zone).toLocalDate()
                .equals(clock.instant().atZone(zone).toLocalDate());
    }

    private Map<Long, BigDecimal> refundedByLine(long saleId) {
        return ReturnRefunds.byLine(lines, saleId);
    }

    private Map<String, BigDecimal> refundedByMethod(long saleId) {
        return ReturnRefunds.byMethod(payments, saleId);
    }

    private static Map<String, BigDecimal> paidByMethod(Sale sale) {
        Map<String, BigDecimal> paid = new HashMap<>();
        for (Payment payment : sale.getPayments()) {
            paid.merge(payment.getPaymentMethod().getCode(), payment.getAmount(), BigDecimal::add);
        }
        return paid;
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
