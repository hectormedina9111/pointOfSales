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
import com.medinasystems.pos.sales.application.SaleResult;
import com.medinasystems.pos.sales.application.SaleService;
import com.medinasystems.pos.sales.application.SaleSummaryView;
import com.medinasystems.pos.sales.application.SaleView;
import com.medinasystems.pos.sales.application.TicketView;
import com.medinasystems.pos.sales.domain.DiscountType;
import com.medinasystems.pos.sales.domain.OrderType;
import com.medinasystems.pos.sales.domain.SaleStatus;
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

    public SaleController(SaleService sales) {
        this.sales = sales;
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
        SaleFilter filter = new SaleFilter(branchId, cashierId, status, orderType, cashSessionId, cashRegisterId,
                customerId, search, minTotal, maxTotal, from, to);
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
                                 @Size(max = 4) String cardLast4) {

        SaleData.PaymentLine toData() {
            return new SaleData.PaymentLine(paymentMethodCode, amount, amountReceived, reference, cardLast4);
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
}
