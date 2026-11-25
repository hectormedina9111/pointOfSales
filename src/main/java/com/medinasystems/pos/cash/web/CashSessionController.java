package com.medinasystems.pos.cash.web;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.cash.application.CashMovementView;
import com.medinasystems.pos.cash.application.CashSessionData;
import com.medinasystems.pos.cash.application.CashSessionDetailView;
import com.medinasystems.pos.cash.application.CashSessionService;
import com.medinasystems.pos.cash.application.CashSessionView;
import com.medinasystems.pos.cash.application.SessionSummaryView;
import com.medinasystems.pos.cash.domain.CashMovementType;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.shared.StoreOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Cash sessions. Opening, movements and closing are store operations; the reads follow the branches each node sees.
 */
@RestController
@RequestMapping("/api/v1/cash-sessions")
public class CashSessionController {

    private final CashSessionService sessions;

    public CashSessionController(CashSessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @StoreOnly
    public CashSessionDetailView open(@Valid @RequestBody OpenRequest request) {
        return sessions.open(request.toData());
    }

    @GetMapping("/current")
    public CashSessionDetailView current() {
        return sessions.current();
    }

    @GetMapping("/{id}")
    public CashSessionDetailView get(@PathVariable Long id) {
        return sessions.get(id);
    }

    @GetMapping
    public PageResponse<CashSessionView> list(@RequestParam(required = false) CashSessionStatus status,
                                              @RequestParam(required = false) Long cashRegisterId,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) Long branchId,
                                              @RequestParam(required = false) Instant from,
                                              @RequestParam(required = false) Instant to,
                                              @PageableDefault(sort = {"openedAt", "id"},
                                                      direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(sessions.list(status, cashRegisterId, userId, branchId, from, to, pageable));
    }

    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @StoreOnly
    public CashMovementView addMovement(@PathVariable Long id, @Valid @RequestBody MovementRequest request) {
        return sessions.addMovement(id, request.toData());
    }

    @GetMapping("/{id}/summary")
    public SessionSummaryView summary(@PathVariable Long id) {
        return sessions.summary(id);
    }

    @PostMapping("/{id}/close")
    @StoreOnly
    public CashSessionDetailView close(@PathVariable Long id, @Valid @RequestBody CloseRequest request) {
        return sessions.close(id, request.toData());
    }

    public record OpenRequest(@NotNull Long cashRegisterId, @NotNull @PositiveOrZero BigDecimal openingAmount) {

        CashSessionData.Open toData() {
            return new CashSessionData.Open(cashRegisterId, openingAmount);
        }
    }

    public record MovementRequest(@NotNull CashMovementType type,
                                  @NotNull @Positive BigDecimal amount,
                                  @NotBlank @Size(max = 200) String reason,
                                  @Valid AuthorizationRequest authorization) {

        CashSessionData.Movement toData() {
            return new CashSessionData.Movement(type, amount, reason,
                    authorization == null ? null : authorization.toData());
        }
    }

    /** Credentials of a manager. The password is never printed. */
    public record AuthorizationRequest(@NotBlank @Size(max = 50) String username,
                                       @NotBlank @Size(max = 200) String password) {

        CashSessionData.Authorization toData() {
            return new CashSessionData.Authorization(username, password);
        }

        @Override
        public String toString() {
            return "AuthorizationRequest[username=" + username + ", password=***]";
        }
    }

    public record CloseRequest(@NotEmpty @Valid List<CountRequest> counts,
                               @Size(max = 500) String notes) {

        CashSessionData.Close toData() {
            return new CashSessionData.Close(counts.stream().map(CountRequest::toData).toList(), notes);
        }
    }

    public record CountRequest(@NotBlank @Size(max = 20) String paymentMethodCode,
                               @NotNull @PositiveOrZero BigDecimal amount) {

        CashSessionData.Count toData() {
            return new CashSessionData.Count(paymentMethodCode, amount);
        }
    }
}
