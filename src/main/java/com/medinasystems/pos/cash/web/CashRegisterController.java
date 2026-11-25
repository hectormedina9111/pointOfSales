package com.medinasystems.pos.cash.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.cash.application.CashRegisterData;
import com.medinasystems.pos.cash.application.CashRegisterService;
import com.medinasystems.pos.cash.application.CashRegisterView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/** Cash registers. Only the central node changes them; every node lists the branches it can see. */
@RestController
@RequestMapping("/api/v1/cash-registers")
public class CashRegisterController {

    private final CashRegisterService registers;

    public CashRegisterController(CashRegisterService registers) {
        this.registers = registers;
    }

    @GetMapping
    public PageResponse<CashRegisterView> list(@RequestParam(required = false) Long branchId,
                                               @RequestParam(required = false) Boolean active,
                                               @PageableDefault(sort = {"branchId", "code"}) Pageable pageable) {
        return PageResponse.from(registers.list(branchId, active, pageable));
    }

    @GetMapping("/{id}")
    public CashRegisterView get(@PathVariable Long id) {
        return registers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public CashRegisterView create(@Valid @RequestBody CashRegisterRequest request) {
        return registers.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public CashRegisterView update(@PathVariable Long id, @Valid @RequestBody CashRegisterRequest request) {
        return registers.update(id, request.toData());
    }

    /** The active flag defaults to true when omitted. The service checks the branch and the code. */
    public record CashRegisterRequest(@NotNull Long branchId,
                                      @NotBlank @Size(max = 10) String code,
                                      @NotBlank @Size(max = 80) String name,
                                      @NotBlank @Size(max = 20) String receiptPrefix,
                                      Boolean active) {

        CashRegisterData toData() {
            return new CashRegisterData(branchId, code, name, receiptPrefix, active == null || active);
        }
    }
}
