package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.CashRegisterDirectory;
import com.medinasystems.pos.cash.CashRegisterInfo;
import com.medinasystems.pos.cash.OpenSessionInfo;
import com.medinasystems.pos.cash.OpenSessions;
import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.pricing.PriceQuote;
import com.medinasystems.pos.pricing.ProductPricing;
import com.medinasystems.pos.sales.domain.Discount;
import com.medinasystems.pos.sales.domain.PaymentDraft;
import com.medinasystems.pos.sales.domain.PaymentMethod;
import com.medinasystems.pos.sales.domain.PaymentRules;
import com.medinasystems.pos.sales.domain.PricedLine;
import com.medinasystems.pos.sales.domain.ReceiptCounter;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleCalculator;
import com.medinasystems.pos.sales.domain.SaleContext;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleTotals;
import com.medinasystems.pos.sales.infrastructure.PaymentMethodRepository;
import com.medinasystems.pos.sales.infrastructure.ReceiptCounterRepository;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;
import com.medinasystems.pos.sales.infrastructure.SaleSpecifications;
import com.medinasystems.pos.security.ActionAuthorizer;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.security.UserInfo;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Sales of a store: quotes, holds, charges and the reads. A charge recomputes everything on the server, checks the
 * discounts and the payments, and numbers the receipt under a lock on the register. Every operation works in the
 * branch of the node, and the holds and charges work in the open cash session of the signed-in cashier.
 */
@Service
@Transactional
public class SaleService {

    /** Privilege that lets a cashier give discounts above the maximum without a manager's authorization. */
    static final String DISCOUNT_PRIVILEGE = "ACTION_SALE_DISCOUNT_OVERRIDE";

    private final SaleRepository sales;
    private final PaymentMethodRepository methods;
    private final ReceiptCounterRepository counters;
    private final SaleableProducts products;
    private final ProductPricing pricing;
    private final OpenSessions openSessions;
    private final BranchDirectory branches;
    private final CompanyDirectory companies;
    private final CashRegisterDirectory registers;
    private final UserDirectory users;
    private final ActionAuthorizer authorizer;
    private final BranchScope scope;
    private final SalesProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SaleService(SaleRepository sales, PaymentMethodRepository methods, ReceiptCounterRepository counters,
                       SaleableProducts products, ProductPricing pricing, OpenSessions openSessions,
                       BranchDirectory branches, CompanyDirectory companies, CashRegisterDirectory registers,
                       UserDirectory users, ActionAuthorizer authorizer, BranchScope scope,
                       SalesProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.sales = sales;
        this.methods = methods;
        this.counters = counters;
        this.products = products;
        this.pricing = pricing;
        this.openSessions = openSessions;
        this.branches = branches;
        this.companies = companies;
        this.registers = registers;
        this.users = users;
        this.authorizer = authorizer;
        this.scope = scope;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Prices a draft sale with the branch prices of the store. Nothing is stored. The flag tells whether the discounts
     * need a manager, so the screen can ask for the authorization before the charge.
     */
    @Transactional(readOnly = true)
    public SaleQuoteView quote(SaleData.Draft data) {
        long userId = currentUserId();
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()), storeBranchId());
        return SaleQuoteView.from(totals, needsAuthorization(totals, userId));
    }

    /**
     * Saves a sale on hold in the open session of the cashier. The discounts are checked when the sale is charged, not
     * here, so a hold never needs a manager.
     */
    public SaleView createHold(SaleData.Draft data) {
        long userId = currentUserId();
        OpenSessionInfo session = currentSession(userId);
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                session.branchId());
        Sale sale = Sale.open(context(session, userId), sales.nextInternalNumber(), totals, null, data.notes());
        return SaleView.from(sales.save(sale));
    }

    /** The holds of the register of the open session, newest first. Empty when the cashier has no open session. */
    @Transactional(readOnly = true)
    public Page<SaleSummaryView> listHolds(Pageable pageable) {
        Optional<OpenSessionInfo> session = openSessions.currentFor(currentUserId());
        if (session.isEmpty()) {
            return Page.empty(pageable);
        }
        return sales.findByStatusAndCashRegisterId(SaleStatus.ON_HOLD, session.get().cashRegisterId(), pageable)
                .map(sale -> SaleSummaryView.from(sale));
    }

    @Transactional(readOnly = true)
    public SaleView getHold(long id) {
        OpenSessionInfo session = currentSession(currentUserId());
        return SaleView.from(findHold(id, session.cashRegisterId()));
    }

    /**
     * Deletes a hold. This is a real delete, not a cancellation: a hold is not a document yet, because nothing was
     * charged. Once charged, the sale is a document and cannot be deleted.
     */
    public void deleteHold(long id) {
        OpenSessionInfo session = currentSession(currentUserId());
        sales.delete(findHold(id, session.cashRegisterId()));
    }

    /**
     * Charges a sale. The totals and the payments are recomputed here; the client's totals are never used. A charge
     * with an idempotency key already used returns the sale that key created, and creates nothing.
     */
    public SaleResult checkout(SaleData.Checkout data) {
        long userId = currentUserId();
        Optional<Sale> charged = sales.findByIdempotencyKey(data.idempotencyKey());
        if (charged.isPresent()) {
            return new SaleResult(SaleView.from(charged.get()), false);
        }

        OpenSessionInfo session = currentSession(userId);
        // Serializes the charges of the register. A second request with the same key waits here and then finds the
        // sale that the first one committed.
        ReceiptCounter counter = lockCounter(session.cashRegisterId());
        charged = sales.findByIdempotencyKey(data.idempotencyKey());
        if (charged.isPresent()) {
            return new SaleResult(SaleView.from(charged.get()), false);
        }

        SaleContext context = context(session, userId);
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                session.branchId());
        Long authorizedBy = discountAuthorization(totals, data.authorization(), userId);
        List<PaymentDraft> drafts = paymentDrafts(data.payments());
        BigDecimal change = PaymentRules.check(totals.total(), drafts);

        Sale sale;
        if (data.holdId() == null) {
            sale = Sale.open(context, sales.nextInternalNumber(), totals, authorizedBy, data.notes());
        } else {
            sale = findHold(data.holdId(), session.cashRegisterId());
            sale.reprice(context, totals, authorizedBy, data.notes());
        }

        String receiptNumber = session.receiptPrefix() + String.format(Locale.ROOT, "%08d", counter.next());
        sale.pay(receiptNumber, data.idempotencyKey(), drafts, change, clock.instant());
        counters.save(counter);
        Sale saved = sales.saveAndFlush(sale);
        events.publishEvent(AuditEvent.created("SALE_PAID", "sale", saved.getId(), paidSnapshot(saved)));
        return new SaleResult(SaleView.from(saved), true);
    }

    @Transactional(readOnly = true)
    public SaleView get(long id) {
        return SaleView.from(findVisible(id));
    }

    /**
     * Sales the node can see, newest first. A store lists the sales of its branch; the central node lists all of them.
     * A store without a branch sees none.
     */
    @Transactional(readOnly = true)
    public Page<SaleSummaryView> list(SaleStatus status, Long cashSessionId, Long cashierId, Instant from, Instant to,
                                      Pageable pageable) {
        Long branchId = null;
        if (!scope.allBranches()) {
            Optional<Long> node = scope.nodeBranchId();
            if (node.isEmpty()) {
                return Page.empty(pageable);
            }
            branchId = node.get();
        }
        return sales.findAll(SaleSpecifications.matching(branchId, status, cashSessionId, cashierId, from, to),
                pageable).map(sale -> SaleSummaryView.from(sale));
    }

    /** The data of the printed ticket. Only a paid sale has one. */
    @Transactional(readOnly = true)
    public TicketView ticket(long id) {
        Sale sale = findVisible(id);
        if (sale.getStatus() != SaleStatus.PAID) {
            throw new BusinessRuleException("SALE_NOT_PAID", "Only a paid sale has a ticket");
        }
        CompanyInfo company = companies.find().orElseThrow(() -> new BusinessRuleException(
                "TICKET_COMPANY_NOT_CONFIGURED", "Configure the company before printing tickets"));
        BranchInfo branch = branches.info(sale.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("branch", sale.getBranchId()));
        CashRegisterInfo register = registers.find(sale.getCashRegisterId())
                .orElseThrow(() -> new ResourceNotFoundException("cash_register", sale.getCashRegisterId()));
        UserInfo cashier = users.find(sale.getCashierId())
                .orElseThrow(() -> new ResourceNotFoundException("app_user", sale.getCashierId()));
        ZoneId zone = ZoneId.of(company.timeZone());
        return TicketView.of(sale, company, branch, register, cashier, zone);
    }

    /**
     * Prices the lines with the branch's prices, computes the totals and checks the products. The price of each line
     * comes from the pricing module at the quantity of the line, so a price list applies by quantity.
     */
    private SaleTotals price(List<SaleData.Line> lines, Discount saleDiscount, long branchId) {
        List<PricedLine> priced = new ArrayList<>();
        for (SaleData.Line line : lines == null ? List.<SaleData.Line>of() : lines) {
            SaleableProduct product = products.find(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("product", line.productId()));
            if (!product.active()) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "The product is inactive");
            }
            PriceQuote quote = pricing.quote(product.id(), line.quantity(), branchId, null);
            priced.add(new PricedLine(product.id(), product.sku(), product.name(), line.quantity(),
                    quote.unitPrice(), product.cost(), quote.taxCode(), quote.taxRate(), quote.priceListId(),
                    product.requiresExclusiveInvoice(), Discount.of(line.discountType(), line.discountValue())));
        }
        return SaleCalculator.calculate(priced, saleDiscount, properties.maxDiscountPercent());
    }

    /** True when a discount is above the maximum and the current user cannot give it alone. */
    private boolean needsAuthorization(SaleTotals totals, long userId) {
        return totals.exceedsMaxDiscount() && !authorizer.hasPrivilege(userId, DISCOUNT_PRIVILEGE);
    }

    /**
     * Who authorized the discounts of a charge: nobody when none is above the maximum, the cashier when they hold the
     * privilege, a manager with the credentials otherwise.
     */
    private Long discountAuthorization(SaleTotals totals, SaleData.Authorization authorization, long userId) {
        if (!totals.exceedsMaxDiscount()) {
            return null;
        }
        if (authorizer.hasPrivilege(userId, DISCOUNT_PRIVILEGE)) {
            return userId;
        }
        if (authorization == null) {
            throw new BusinessRuleException("DISCOUNT_AUTHORIZATION_REQUIRED", "A manager must authorize this discount");
        }
        return authorizer.authorize(authorization.username(), authorization.password(), DISCOUNT_PRIVILEGE);
    }

    private List<PaymentDraft> paymentDrafts(List<SaleData.PaymentLine> payments) {
        List<PaymentDraft> drafts = new ArrayList<>();
        for (SaleData.PaymentLine payment : payments == null ? List.<SaleData.PaymentLine>of() : payments) {
            PaymentMethod method = methods.findByCode(payment.paymentMethodCode()).orElseThrow(
                    () -> new BusinessRuleException("PAYMENT_METHOD_NOT_FOUND", "The payment method does not exist"));
            drafts.add(new PaymentDraft(method, payment.amount(), payment.amountReceived(), payment.reference(),
                    payment.cardLast4()));
        }
        return drafts;
    }

    private ReceiptCounter lockCounter(long cashRegisterId) {
        return counters.findForUpdate(cashRegisterId)
                .orElseGet(() -> counters.saveAndFlush(new ReceiptCounter(cashRegisterId)));
    }

    private Sale findHold(long id, long cashRegisterId) {
        return sales.findByIdAndStatusAndCashRegisterId(id, SaleStatus.ON_HOLD, cashRegisterId)
                .orElseThrow(() -> new ResourceNotFoundException("sale", id));
    }

    /** A sale the node cannot see answers 404, as if it did not exist. */
    private Sale findVisible(long id) {
        return sales.findById(id).filter(sale -> scope.canSee(sale.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("sale", id));
    }

    private OpenSessionInfo currentSession(long userId) {
        return openSessions.currentFor(userId)
                .orElseThrow(() -> new BusinessRuleException("NO_OPEN_SESSION", "The cashier has no open cash session"));
    }

    private long storeBranchId() {
        return scope.nodeBranchId().orElseThrow(
                () -> new BusinessRuleException("STORE_BRANCH_REQUIRED", "This store has no branch assigned"));
    }

    private static SaleContext context(OpenSessionInfo session, long userId) {
        return new SaleContext(session.branchId(), session.cashRegisterId(), session.sessionId(), userId);
    }

    private static Map<String, Object> paidSnapshot(Sale sale) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("receiptNumber", sale.getReceiptNumber());
        values.put("status", sale.getStatus().name());
        values.put("cashRegisterId", sale.getCashRegisterId());
        values.put("cashSessionId", sale.getCashSessionId());
        values.put("total", sale.getTotal());
        values.put("amountPaid", sale.getAmountPaid());
        values.put("changeDue", sale.getChangeDue());
        values.put("discountTotal", sale.getDiscountTotal());
        values.put("discountAuthorizedBy", sale.getDiscountAuthorizedBy());
        return values;
    }

    private static long currentUserId() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaim("uid") instanceof Number uid) {
            return uid.longValue();
        }
        throw new IllegalStateException("The request has no signed-in user");
    }
}
