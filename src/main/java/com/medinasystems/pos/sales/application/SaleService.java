package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
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
import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.pricing.PriceQuote;
import com.medinasystems.pos.pricing.ProductPricing;
import com.medinasystems.pos.sales.domain.Discount;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.PaymentDraft;
import com.medinasystems.pos.sales.domain.PaymentMethod;
import com.medinasystems.pos.sales.domain.PaymentRules;
import com.medinasystems.pos.sales.domain.PricedLine;
import com.medinasystems.pos.sales.domain.ReceiptCounter;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleCalculator;
import com.medinasystems.pos.sales.domain.SaleContext;
import com.medinasystems.pos.sales.domain.SaleOrder;
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
import com.medinasystems.pos.sync.Outbox;

/**
 * Sales of a store: quotes, orders on hold, charges and the reads. An order names its customer and its order type, and
 * the lines are priced with the customer's price list first. A discount above the maximum and a changed price need a
 * manager, checked when the order is saved. A charge recomputes everything on the server (unless it charges a hold, which
 * keeps its stored prices), checks the payments, and numbers the receipt under a lock on the register. Every operation
 * works in the branch of the node, and the holds and charges work in the open cash session of the signed-in cashier.
 */
@Service
@Transactional
public class SaleService {

    /** Privilege that lets a cashier give discounts above the maximum without a manager's authorization. */
    static final String DISCOUNT_PRIVILEGE = "ACTION_SALE_DISCOUNT_OVERRIDE";

    /** Privilege that lets a cashier change a line's price without a manager's authorization. */
    static final String PRICE_PRIVILEGE = "ACTION_SALE_PRICE_OVERRIDE";

    private final SaleRepository sales;
    private final PaymentMethodRepository methods;
    private final ReceiptCounterRepository counters;
    private final SaleableProducts products;
    private final ProductPricing pricing;
    private final CustomerDirectory customers;
    private final OpenSessions openSessions;
    private final BranchDirectory branches;
    private final CompanyDirectory companies;
    private final CashRegisterDirectory registers;
    private final UserDirectory users;
    private final ActionAuthorizer authorizer;
    private final BranchScope scope;
    private final SalesProperties properties;
    private final Outbox outbox;
    private final AuditorAware<String> auditor;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SaleService(SaleRepository sales, PaymentMethodRepository methods, ReceiptCounterRepository counters,
                       SaleableProducts products, ProductPricing pricing, CustomerDirectory customers,
                       OpenSessions openSessions, BranchDirectory branches, CompanyDirectory companies,
                       CashRegisterDirectory registers, UserDirectory users, ActionAuthorizer authorizer,
                       BranchScope scope, SalesProperties properties, Outbox outbox,
                       AuditorAware<String> auditor, ApplicationEventPublisher events, Clock clock) {
        this.sales = sales;
        this.methods = methods;
        this.counters = counters;
        this.products = products;
        this.pricing = pricing;
        this.customers = customers;
        this.openSessions = openSessions;
        this.branches = branches;
        this.companies = companies;
        this.registers = registers;
        this.users = users;
        this.authorizer = authorizer;
        this.scope = scope;
        this.properties = properties;
        this.outbox = outbox;
        this.auditor = auditor;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Prices a draft order with the branch prices of the store and the customer's list first. Nothing is stored. The
     * flags tell which authorizations the order will need, so the screen can ask for them before it is saved.
     */
    @Transactional(readOnly = true)
    public SaleQuoteView quote(SaleData.Draft data) {
        long userId = currentUserId();
        OrderType.requested(data.orderType());
        CustomerInfo customer = activeCustomer(data.customerId());
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                storeBranchId(), customer);
        return SaleQuoteView.from(totals, needsAuthorization(totals.exceedsMaxDiscount(), DISCOUNT_PRIVILEGE, userId),
                needsAuthorization(overridesPrice(totals), PRICE_PRIVILEGE, userId));
    }

    /**
     * Saves an order on hold in the open session of the cashier. The discounts and the price changes are checked here,
     * exactly as a charge checks them, so the authorization is given once, when the order is saved.
     */
    public SaleView createHold(SaleData.Draft data) {
        long userId = currentUserId();
        OpenSessionInfo session = currentSession(userId);
        OrderType orderType = OrderType.requested(data.orderType());
        CustomerInfo customer = activeCustomer(data.customerId());
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                session.branchId(), customer);
        SaleOrder order = order(customer, orderType, totals, data.authorization(), data.notes(), userId);
        Sale sale = Sale.open(context(session, userId), sales.nextInternalNumber(), totals, order);
        return view(sales.save(sale));
    }

    /**
     * Replaces the customer, the order type, the lines, the discount and the notes of a hold of the open register. The
     * same checks as when the hold was saved apply.
     */
    public SaleView updateHold(long id, SaleData.Draft data) {
        long userId = currentUserId();
        OpenSessionInfo session = currentSession(userId);
        Sale sale = findHold(id, session.cashRegisterId());
        OrderType orderType = OrderType.requested(data.orderType());
        CustomerInfo customer = activeCustomer(data.customerId());
        SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                session.branchId(), customer);
        SaleOrder order = order(customer, orderType, totals, data.authorization(), data.notes(), userId);
        sale.reprice(context(session, userId), totals, order);
        return view(sale);
    }

    /** The holds of the register of the open session, newest first. Empty when the cashier has no open session. */
    @Transactional(readOnly = true)
    public Page<SaleSummaryView> listHolds(Pageable pageable) {
        Optional<OpenSessionInfo> session = openSessions.currentFor(currentUserId());
        if (session.isEmpty()) {
            return Page.empty(pageable);
        }
        return summaries(sales.findByStatusAndCashRegisterId(SaleStatus.ON_HOLD, session.get().cashRegisterId(),
                pageable));
    }

    @Transactional(readOnly = true)
    public SaleView getHold(long id) {
        OpenSessionInfo session = currentSession(currentUserId());
        return view(findHold(id, session.cashRegisterId()));
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
     * Charges a sale in one step. The totals and the payments are recomputed here; the client's totals are never used. A
     * charge with an idempotency key already used returns the sale that key created, and creates nothing.
     */
    public SaleResult checkout(SaleData.Checkout data) {
        long userId = currentUserId();
        return charge(userId, data.idempotencyKey(), data.payments(), session -> {
            OrderType orderType = OrderType.requested(data.orderType());
            CustomerInfo customer = activeCustomer(data.customerId());
            SaleContext context = context(session, userId);
            SaleTotals totals = price(data.lines(), Discount.of(data.discountType(), data.discountValue()),
                    session.branchId(), customer);
            SaleOrder order = order(customer, orderType, totals, data.authorization(), data.notes(), userId);
            if (data.holdId() == null) {
                return Sale.open(context, sales.nextInternalNumber(), totals, order);
            }
            Sale sale = findHold(data.holdId(), session.cashRegisterId());
            sale.reprice(context, totals, order);
            return sale;
        });
    }

    /**
     * Charges a sale on hold with the lines and prices it was saved with. Nothing is priced or authorized again. The
     * payments, the receipt and the idempotency key work as in {@link #checkout}.
     */
    public SaleResult checkoutHold(SaleData.HoldCheckout data) {
        long userId = currentUserId();
        return charge(userId, data.idempotencyKey(), data.payments(), session -> {
            Sale sale = findHold(data.holdId(), session.cashRegisterId());
            if (data.notes() != null) {
                sale.changeNotes(data.notes());
            }
            return sale;
        });
    }

    @Transactional(readOnly = true)
    public SaleView get(long id) {
        return view(findVisible(id));
    }

    /**
     * Sales the node can see, newest first. A store lists the sales of its branch; the central node lists all of them.
     * A store without a branch sees none. Without {@code ACTION_SALES_VIEW_ALL} the list shows only the signed-in
     * cashier's own sales, whatever {@code cashierId} asks for. {@code branchId} narrows the list to one branch; on a
     * store, another branch gives an empty list.
     */
    @Transactional(readOnly = true)
    public Page<SaleSummaryView> list(SaleStatus status, Long cashSessionId, Long cashierId, Long branchId,
                                      Instant from, Instant to, Pageable pageable) {
        long current = currentUserId();
        Long owner = authorizer.hasPrivilege(current, ActionAuthorizer.SALES_VIEW_ALL_PRIVILEGE)
                ? cashierId : Long.valueOf(current);
        Long filterBranch = branchId;
        if (!scope.allBranches()) {
            Optional<Long> node = scope.nodeBranchId();
            if (node.isEmpty() || (branchId != null && !branchId.equals(node.get()))) {
                return Page.empty(pageable);
            }
            filterBranch = node.get();
        }
        return summaries(sales.findAll(
                SaleSpecifications.matching(filterBranch, status, cashSessionId, owner, from, to), pageable));
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
        CustomerInfo customer = findCustomer(sale.getCustomerId());
        ZoneId zone = ZoneId.of(company.timeZone());
        return TicketView.of(sale, company, branch, register, cashier, customer, zone);
    }

    /**
     * The shared part of a charge, a hold charge, and nothing else. An idempotency key already used returns its sale
     * without creating anything. Otherwise the register counter is locked, the sale is prepared (priced, or found on
     * hold), the payments are checked against its stored total, and the sale is paid with the next receipt number.
     */
    private SaleResult charge(long userId, String idempotencyKey, List<SaleData.PaymentLine> payments,
                              Function<OpenSessionInfo, Sale> prepare) {
        Optional<Sale> charged = sales.findByIdempotencyKey(idempotencyKey);
        if (charged.isPresent()) {
            return new SaleResult(view(charged.get()), false);
        }

        OpenSessionInfo session = currentSession(userId);
        // Serializes the charges of the register. A second request with the same key waits here and then finds the
        // sale that the first one committed.
        ReceiptCounter counter = lockCounter(session.cashRegisterId());
        charged = sales.findByIdempotencyKey(idempotencyKey);
        if (charged.isPresent()) {
            return new SaleResult(view(charged.get()), false);
        }

        Sale sale = prepare.apply(session);
        List<PaymentDraft> drafts = paymentDrafts(payments);
        BigDecimal change = PaymentRules.check(sale.getTotal(), drafts);
        String receiptNumber = session.receiptPrefix() + String.format(Locale.ROOT, "%08d", counter.next());
        sale.pay(receiptNumber, idempotencyKey, drafts, change, clock.instant());
        counters.save(counter);
        Sale saved = sales.saveAndFlush(sale);
        events.publishEvent(AuditEvent.created("SALE_PAID", "sale", saved.getId(), paidSnapshot(saved)));
        outbox.enqueue(SaleDocument.EVENT_TYPE, SaleDocument.of(saved));
        return new SaleResult(view(saved), true);
    }

    /**
     * Prices the lines with the customer's list first and then the branch rules, and computes the totals. A line with a
     * unit price different from its list price is an override: it keeps the list price and is marked as overridden. The
     * authorization is checked elsewhere, so this method only prices.
     */
    private SaleTotals price(List<SaleData.Line> lines, Discount saleDiscount, long branchId, CustomerInfo customer) {
        List<PricedLine> priced = new ArrayList<>();
        for (SaleData.Line line : lines == null ? List.<SaleData.Line>of() : lines) {
            SaleableProduct product = products.find(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("product", line.productId()));
            if (!product.active()) {
                throw new BusinessRuleException("PRODUCT_INACTIVE", "The product is inactive");
            }
            PriceQuote quote = pricing.quote(product.id(), line.quantity(), branchId, customer.priceListId(), null);
            BigDecimal listPrice = quote.unitPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal requested = line.unitPrice() == null
                    ? null : line.unitPrice().setScale(2, RoundingMode.HALF_UP);
            boolean overridden = requested != null && requested.compareTo(listPrice) != 0;
            priced.add(new PricedLine(product.id(), product.sku(), product.name(), line.quantity(),
                    overridden ? requested : listPrice, product.cost(), quote.taxCode(), quote.taxRate(),
                    quote.priceListId(), product.requiresExclusiveInvoice(),
                    Discount.of(line.discountType(), line.discountValue()), listPrice, overridden));
        }
        return SaleCalculator.calculate(priced, saleDiscount, properties.maxDiscountPercent());
    }

    /** True when any line sells at a price other than its list price. */
    private static boolean overridesPrice(SaleTotals totals) {
        return totals.lines().stream().anyMatch(line -> line.line().priceOverridden());
    }

    /** True when the authorization is needed and the current user cannot give it alone. */
    private boolean needsAuthorization(boolean required, String privilege, long userId) {
        return required && !authorizer.hasPrivilege(userId, privilege);
    }

    /**
     * The header of an order with the authorizations it needs. The discount is checked first, then the price. Each one is
     * authorized by the cashier when they hold the privilege, or by a manager with the credentials otherwise.
     */
    private SaleOrder order(CustomerInfo customer, OrderType orderType, SaleTotals totals,
                            SaleData.Authorization authorization, String notes, long userId) {
        Long discountBy = authorizedBy(totals.exceedsMaxDiscount(), DISCOUNT_PRIVILEGE, authorization, userId,
                "DISCOUNT_AUTHORIZATION_REQUIRED", "A manager must authorize this discount");
        Long priceBy = authorizedBy(overridesPrice(totals), PRICE_PRIVILEGE, authorization, userId,
                "PRICE_OVERRIDE_AUTHORIZATION_REQUIRED", "A manager must authorize this price change");
        return new SaleOrder(customer.id(), orderType, discountBy, priceBy, notes);
    }

    /**
     * Who authorizes a change that needs a manager: nobody when it is not required, the current user when they hold the
     * privilege, and the manager with the given credentials otherwise. A wrong password counts toward the lockout.
     *
     * @throws BusinessRuleException with {@code code} when the change needs an authorization and none was given
     */
    private Long authorizedBy(boolean required, String privilege, SaleData.Authorization authorization, long userId,
                              String code, String message) {
        if (!required) {
            return null;
        }
        if (authorizer.hasPrivilege(userId, privilege)) {
            return userId;
        }
        if (authorization == null) {
            throw new BusinessRuleException(code, message);
        }
        return authorizer.authorize(authorization.username(), authorization.password(), privilege);
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

    /**
     * The counter of the register, locked for the rest of the charge. It is created first with ON CONFLICT DO NOTHING, so
     * two first charges of a register do not fail on the primary key: the second waits, then finds the row.
     */
    private ReceiptCounter lockCounter(long cashRegisterId) {
        counters.insertIfAbsent(cashRegisterId, clock.instant(), auditor.getCurrentAuditor().orElse("system"));
        return counters.findForUpdate(cashRegisterId)
                .orElseThrow(() -> new IllegalStateException("The receipt counter of the register is missing"));
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

    /** The customer of an order: the final consumer when none is named. The customer must exist on this node. */
    private CustomerInfo findCustomer(Long customerId) {
        long id = customerId == null ? CustomerDirectory.FINAL_CONSUMER_ID : customerId;
        return customers.find(id).orElseThrow(() -> new ResourceNotFoundException("customer", id));
    }

    /** The customer of a new order, who must be active. */
    private CustomerInfo activeCustomer(Long customerId) {
        CustomerInfo customer = findCustomer(customerId);
        if (!customer.active()) {
            throw new BusinessRuleException("CUSTOMER_INACTIVE", "The customer is inactive");
        }
        return customer;
    }

    private SaleView view(Sale sale) {
        return SaleView.from(sale, findCustomer(sale.getCustomerId()));
    }

    /** Lists a page of sales, looking up each customer once even when many sales share it. */
    private Page<SaleSummaryView> summaries(Page<Sale> page) {
        Map<Long, CustomerInfo> byCustomer = new HashMap<>();
        for (Sale sale : page.getContent()) {
            byCustomer.computeIfAbsent(sale.getCustomerId(), this::findCustomer);
        }
        return page.map(sale -> SaleSummaryView.from(sale, byCustomer.get(sale.getCustomerId())));
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
        values.put("customerId", sale.getCustomerId());
        values.put("orderType", sale.getOrderType().name());
        values.put("total", sale.getTotal());
        values.put("amountPaid", sale.getAmountPaid());
        values.put("changeDue", sale.getChangeDue());
        values.put("discountTotal", sale.getDiscountTotal());
        values.put("discountAuthorizedBy", sale.getDiscountAuthorizedBy());
        values.put("priceAuthorizedBy", sale.getPriceAuthorizedBy());
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
