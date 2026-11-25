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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sales.application.SaleData;
import com.medinasystems.pos.sales.application.SaleQuoteView;
import com.medinasystems.pos.sales.application.SaleResult;
import com.medinasystems.pos.sales.application.SaleService;
import com.medinasystems.pos.sales.application.SaleSummaryView;
import com.medinasystems.pos.sales.application.SaleView;
import com.medinasystems.pos.sales.application.TicketView;
import com.medinasystems.pos.sales.domain.DiscountType;
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

    @GetMapping
    public PageResponse<SaleSummaryView> list(@RequestParam(required = false) SaleStatus status,
                                              @RequestParam(required = false) Long cashSessionId,
                                              @RequestParam(required = false) Long cashierId,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @PageableDefault(sort = {"createdAt", "id"},
                                                      direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(sales.list(status, cashSessionId, cashierId, branchId, from, to, pageable));
    }

    @GetMapping("/{id}")
    public SaleView get(@PathVariable Long id) {
        return sales.get(id);
    }

    @GetMapping("/{id}/ticket")
    public TicketView ticket(@PathVariable Long id) {
        return sales.ticket(id);
    }

    public record LineRequest(@NotNull Long productId,
                              @NotNull @Positive BigDecimal quantity,
                              DiscountType discountType,
                              @PositiveOrZero BigDecimal discountValue) {

        SaleData.Line toData() {
            return new SaleData.Line(productId, quantity, discountType, discountValue);
        }
    }

    public record DraftRequest(@NotEmpty @Valid List<LineRequest> lines,
                               DiscountType discountType,
                               @PositiveOrZero BigDecimal discountValue,
                               @Size(max = 500) String notes) {

        SaleData.Draft toData() {
            return new SaleData.Draft(lines.stream().map(line -> line.toData()).toList(), discountType,
                    discountValue, notes);
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
                                  @NotEmpty @Valid List<PaymentRequest> payments,
                                  @NotBlank @Size(max = 64) String idempotencyKey,
                                  @Valid AuthorizationRequest authorization,
                                  @Size(max = 500) String notes) {

        SaleData.Checkout toData() {
            return new SaleData.Checkout(holdId, lines.stream().map(line -> line.toData()).toList(), discountType,
                    discountValue, payments.stream().map(payment -> payment.toData()).toList(), idempotencyKey,
                    authorization == null ? null : authorization.toData(), notes);
        }
    }
}
