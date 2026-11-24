package com.medinasystems.pos.catalog.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.catalog.application.UnitOfMeasureData;
import com.medinasystems.pos.catalog.application.UnitOfMeasureService;
import com.medinasystems.pos.catalog.application.UnitOfMeasureView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/units-of-measure")
public class UnitOfMeasureController {

    private final UnitOfMeasureService units;

    public UnitOfMeasureController(UnitOfMeasureService units) {
        this.units = units;
    }

    @GetMapping
    public PageResponse<UnitOfMeasureView> list(@PageableDefault(sort = "code") Pageable pageable) {
        return PageResponse.from(units.list(pageable));
    }

    @GetMapping("/{id}")
    public UnitOfMeasureView get(@PathVariable Long id) {
        return units.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public UnitOfMeasureView create(@Valid @RequestBody UnitOfMeasureRequest request) {
        return units.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public UnitOfMeasureView update(@PathVariable Long id, @Valid @RequestBody UnitOfMeasureRequest request) {
        return units.update(id, request.toData());
    }

    /** Defaults: no decimals and active. */
    public record UnitOfMeasureRequest(@NotBlank @Size(max = 10) String code,
                                       @NotBlank @Size(max = 50) String name,
                                       Boolean allowsDecimals,
                                       Boolean active) {

        UnitOfMeasureData toData() {
            return new UnitOfMeasureData(code, name, Boolean.TRUE.equals(allowsDecimals),
                    active == null || active);
        }
    }
}
