package com.medinasystems.pos.sales.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sales.application.SaleData;
import com.medinasystems.pos.sales.application.SaleFilter;
import com.medinasystems.pos.sales.application.SaleQuoteView;
import com.medinasystems.pos.sales.application.ReturnQuoteView;
import com.medinasystems.pos.sales.application.ReturnableView;
import com.medinasystems.pos.sales.application.SaleResult;
import com.medinasystems.pos.sales.application.SaleReturnService;
import com.medinasystems.pos.sales.application.SaleService;
import com.medinasystems.pos.sales.application.SaleSummaryView;
import com.medinasystems.pos.sales.application.SaleView;
import com.medinasystems.pos.sales.application.TicketView;
import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.SaleStatus;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.shared.StoreOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Sales of the store. Quotes, holds and charges are store operations; the reads follow the branches each node sees.
 * Holds belong to the register of the signed-in cashier's open session.
 */
@RestController
@RequestMapping("/api/v1/sales")
public class SaleController {

    private final SaleService sales;
    private final SaleReturnService returns;

    public SaleController(SaleService sales, SaleReturnService returns) {
        this.sales = sales;
        this.returns = returns;
    }

    @PostMapping("/quote")
    @StoreOnly
    public SaleQuoteView quote(@Valid @RequestBody DraftRequest request) {
        return sales.quote(request.toData());
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    @StoreOnly
    public SaleView createHold(@Valid @RequestBody DraftRequest request) {
        return sales.createHold(request.toData());
    }

    /** Replaces the content of a hold of the open register: its customer, order type, lines, discount and notes. */
    @PutMapping("/holds/{id}")
    @StoreOnly
    public SaleView updateHold(@PathVariable Long id, @Valid @RequestBody DraftRequest request) {
        return sales.updateHold(id, request.toData());
    }

    /** Charges a hold with its stored lines: 201 with the sale, or 200 when the idempotency key already charged it. */
    @PostMapping("/holds/{id}/checkout")
    @StoreOnly
    public ResponseEntity<SaleView> checkoutHold(@PathVariable Long id,
                                                 @Valid @RequestBody HoldCheckoutRequest request) {
        SaleResult result = sales.checkoutHold(request.toData(id));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }

    @GetMapping("/holds")
    public PageResponse<SaleSummaryView> listHolds(@PageableDefault(sort = {"createdAt", "id"},
            direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(sales.listHolds(pageable));
    }

    @GetMapping("/holds/{id}")
    public SaleView getHold(@PathVariable Long id) {
        return sales.getHold(id);
    }

    @DeleteMapping("/holds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @StoreOnly
    public void deleteHold(@PathVariable Long id) {
        sales.deleteHold(id);
    }

    /** Charges a sale: 201 with the new sale, or 200 with the sale that the same idempotency key created before. */
    @PostMapping
    @StoreOnly
    public ResponseEntity<SaleView> checkout(@Valid @RequestBody CheckoutRequest request) {
        SaleResult result = sales.checkout(request.toData());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }

    /**
     * Sales of the node, newest first, of any status. All the filters are optional and are applied in the database:
     * {@code search} matches the receipt number (or the internal number when it is all digits), {@code minTotal} and
     * {@code maxTotal} bound the total, and {@code from} and {@code to} bound the creation time.
     */
    @GetMapping
    public PageResponse<SaleSummaryView> list(@RequestParam(required = false) SaleStatus status,
                                              @RequestParam(required = false) OrderType orderType,
                                              @RequestParam(required = false) SaleType type,
                                              @RequestParam(required = false) Long cashSessionId,
                                              @RequestParam(required = false) Long cashRegisterId,
                                              @RequestParam(required = false) Long cashierId,
                                              @RequestParam(required = false) Long customerId,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(required = false) BigDecimal minTotal,
                                              @RequestParam(required = false) BigDecimal maxTotal,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @PageableDefault(sort = {"createdAt", "id"},
                                                      direction = Sort.Direction.DESC) Pageable pageable) {
        SaleFilter filter = new SaleFilter(branchId, cashierId, status, orderType, type, cashSessionId,
                cashRegisterId, customerId, search, minTotal, maxTotal, from, to);
        return PageResponse.from(sales.list(filter, pageable));
    }

    @GetMapping("/{id}")
    public SaleView get(@PathVariable Long id) {
        return sales.get(id);
    }

    @GetMapping("/{id}/ticket")
    public TicketView ticket(@PathVariable Long id) {
        return sales.ticket(id);
    }

    /** What can be returned from a sale: the lines left to return and the payments that can be refunded. */
    @GetMapping("/{id}/returnable")
    public ReturnableView returnable(@PathVariable Long id) {
        return returns.returnable(id);
    }

    /**
     * The returnable view of a sale found by its receipt number: a sale of this store's branch, or a sale of another branch
     * when this branch accepts such returns (the central node answers then). The store must be online for the second case.
     */
    @GetMapping("/returnable-lookup")
    @StoreOnly
    public ReturnableView returnableLookup(@RequestParam String receiptNumber) {
        return returns.lookup(receiptNumber);
    }

    /** The amounts a return of these lines would refund. Stores nothing and needs no authorization. */
    @PostMapping("/{id}/return-quote")
    @StoreOnly
    public ReturnQuoteView returnQuote(@PathVariable Long id, @Valid @RequestBody ReturnQuoteRequest request) {
        return returns.quote(id, request.toData());
    }

    /** Creates a return: 201 with the RETURN document, or 200 when the idempotency key already created it. */
    @PostMapping("/{id}/returns")
    @StoreOnly
    public ResponseEntity<SaleView> createReturn(@PathVariable Long id, @Valid @RequestBody ReturnRequest request) {
        SaleResult result = returns.create(request.toData(id));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.sale());
    }

    /**
     * One line. {@code unitPrice} is optional: without it the line sells at the list price; with another price the line
     * is an override, which needs an authorization.
     */
    public record LineRequest(@NotNull Long productId,
                              @NotNull @Positive BigDecimal quantity,
                              DiscountType discountType,
                              @PositiveOrZero BigDecimal discountValue,
                              @Positive BigDecimal unitPrice) {

        SaleData.Line toData() {
            return new SaleData.Line(productId, quantity, discountType, discountValue, unitPrice);
        }
    }

    /** A draft order for quotes and holds. A null customer is the final consumer; a null order type is cash. */
    public record DraftRequest(@NotEmpty @Valid List<LineRequest> lines,
                               DiscountType discountType,
                               @PositiveOrZero BigDecimal discountValue,
                               Long customerId,
                               OrderType orderType,
                               @Valid AuthorizationRequest authorization,
                               @Size(max = 500) String notes) {

        SaleData.Draft toData() {
            return new SaleData.Draft(lines.stream().map(line -> line.toData()).toList(), discountType,
                    discountValue, customerId, orderType, authorization == null ? null : authorization.toData(),
                    notes);
        }
    }

    public record PaymentRequest(@NotBlank @Size(max = 20) String paymentMethodCode,
                                 @NotNull @Positive BigDecimal amount,
                                 @PositiveOrZero BigDecimal amountReceived,
                                 @Size(max = 60) String reference,
                                 @Size(max = 4) String cardLast4,
                                 @Size(max = 60) String creditNoteNumber) {

        SaleData.PaymentLine toData() {
            return new SaleData.PaymentLine(paymentMethodCode, amount, amountReceived, reference, cardLast4,
                    creditNoteNumber);
        }
    }

    /** Credentials of a manager. The password is never printed. */
    public record AuthorizationRequest(@NotBlank @Size(max = 50) String username,
                                       @NotBlank @Size(max = 200) String password) {

        SaleData.Authorization toData() {
            return new SaleData.Authorization(username, password);
        }

        @Override
        public String toString() {
            return "AuthorizationRequest[username=" + username + ", password=***]";
        }
    }

    public record CheckoutRequest(Long holdId,
                                  @NotEmpty @Valid List<LineRequest> lines,
                                  DiscountType discountType,
                                  @PositiveOrZero BigDecimal discountValue,
                                  Long customerId,
                                  OrderType orderType,
                                  @NotEmpty @Valid List<PaymentRequest> payments,
                                  @NotBlank @Size(max = 64) String idempotencyKey,
                                  @Valid AuthorizationRequest authorization,
                                  @Size(max = 500) String notes) {

        SaleData.Checkout toData() {
            return new SaleData.Checkout(holdId, lines.stream().map(line -> line.toData()).toList(), discountType,
                    discountValue, customerId, orderType, payments.stream().map(payment -> payment.toData()).toList(),
                    idempotencyKey, authorization == null ? null : authorization.toData(), notes);
        }
    }

    /** Charge of a hold. The lines and prices come from the hold, so only the payments are sent. */
    public record HoldCheckoutRequest(@NotEmpty @Valid List<PaymentRequest> payments,
                                      @NotBlank @Size(max = 64) String idempotencyKey,
                                      @Size(max = 500) String notes) {

        SaleData.HoldCheckout toData(long holdId) {
            return new SaleData.HoldCheckout(holdId, payments.stream().map(payment -> payment.toData()).toList(),
                    idempotencyKey, notes);
        }
    }

    /** One line to return: a line of the original sale and the quantity that comes back. */
    public record ReturnLineRequest(@NotNull Long saleLineId, @NotNull @Positive BigDecimal quantity) {

        SaleData.ReturnLine toData() {
            return new SaleData.ReturnLine(saleLineId, quantity);
        }
    }

    public record ReturnQuoteRequest(@NotEmpty @Valid List<ReturnLineRequest> lines) {

        List<SaleData.ReturnLine> toData() {
            return lines.stream().map(line -> line.toData()).toList();
        }
    }

    /** A refund of a return on the day of the sale: the method, the amount and the reference when the method needs it. */
    public record RefundRequest(@NotBlank @Size(max = 20) String paymentMethodCode,
                                @NotNull @Positive BigDecimal amount,
                                @Size(max = 60) String reference) {

        SaleData.PaymentLine toData() {
            return new SaleData.PaymentLine(paymentMethodCode, amount, null, reference, null, null);
        }
    }

    /**
     * A return. {@code refunds} may be empty: then the return is refunded with a credit note, which is only allowed on a
     * day after the sale. The authorization is needed when the user does not hold the return privilege.
     */
    public record ReturnRequest(@NotEmpty @Valid List<ReturnLineRequest> lines,
                                @NotBlank @Size(max = 300) String reason,
                                @Valid List<RefundRequest> refunds,
                                @Valid AuthorizationRequest authorization,
                                @NotBlank @Size(max = 64) String idempotencyKey) {

        SaleData.Return toData(long saleId) {
            List<SaleData.PaymentLine> refundLines = refunds == null
                    ? List.of() : refunds.stream().map(refund -> refund.toData()).toList();
            return new SaleData.Return(saleId, lines.stream().map(line -> line.toData()).toList(), reason,
                    refundLines, authorization == null ? null : authorization.toData(), idempotencyKey);
        }
    }
}
