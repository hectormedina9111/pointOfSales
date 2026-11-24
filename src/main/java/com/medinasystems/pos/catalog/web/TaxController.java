package com.medinasystems.pos.catalog.web;

import java.math.BigDecimal;

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

import com.medinasystems.pos.catalog.application.TaxData;
import com.medinasystems.pos.catalog.application.TaxService;
import com.medinasystems.pos.catalog.application.TaxView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/taxes")
public class TaxController {

    private final TaxService taxes;

    public TaxController(TaxService taxes) {
        this.taxes = taxes;
    }

    @GetMapping
    public PageResponse<TaxView> list(@RequestParam(required = false) Long countryId,
                                      @PageableDefault(sort = "code") Pageable pageable) {
        return PageResponse.from(taxes.list(countryId, pageable));
    }

    @GetMapping("/{id}")
    public TaxView get(@PathVariable Long id) {
        return taxes.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public TaxView create(@Valid @RequestBody TaxRequest request) {
        return taxes.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public TaxView update(@PathVariable Long id, @Valid @RequestBody TaxRequest request) {
        return taxes.update(id, request.toData());
    }

    /** The rate range and the code uniqueness are checked by the domain and the service (422). */
    public record TaxRequest(@NotNull Long countryId,
                             @NotBlank @Size(max = 20) String code,
                             @NotBlank @Size(max = 80) String name,
                             @NotNull BigDecimal rate,
                             Boolean active) {

        TaxData toData() {
            return new TaxData(countryId, code, name, rate, active == null || active);
        }
    }
}
