package com.medinasystems.pos.organization.web;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.organization.application.CountryData;
import com.medinasystems.pos.organization.application.CountryService;
import com.medinasystems.pos.organization.application.CountryView;
import com.medinasystems.pos.organization.domain.FiscalProviderCode;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/countries")
public class CountryController {

    private final CountryService countries;

    public CountryController(CountryService countries) {
        this.countries = countries;
    }

    @GetMapping
    public PageResponse<CountryView> list(@PageableDefault(sort = "isoCode") Pageable pageable) {
        return PageResponse.from(countries.list(pageable));
    }

    @GetMapping("/{id}")
    public CountryView get(@PathVariable Long id) {
        return countries.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public CountryView create(@Valid @RequestBody CountryRequest request) {
        return countries.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public CountryView update(@PathVariable Long id, @Valid @RequestBody CountryRequest request) {
        return countries.update(id, request.toData());
    }

    /** ISO code, currency and time zone format are checked by the domain, so they answer 422 with a business code. */
    public record CountryRequest(@NotBlank String isoCode,
                                 @NotBlank @Size(max = 80) String name,
                                 @NotBlank String currency,
                                 @NotBlank @Size(max = 50) String timeZone,
                                 @NotBlank @Size(max = 20) String taxIdLabel,
                                 @NotNull FiscalProviderCode fiscalProvider,
                                 @NotNull Boolean active) {

        CountryData toData() {
            return new CountryData(isoCode, name, currency, timeZone, taxIdLabel, fiscalProvider, active);
        }
    }
}
