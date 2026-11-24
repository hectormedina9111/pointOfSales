package com.medinasystems.pos.organization.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.organization.application.CompanyData;
import com.medinasystems.pos.organization.application.CompanyService;
import com.medinasystems.pos.organization.application.CompanyView;
import com.medinasystems.pos.shared.CentralOnly;

@RestController
@RequestMapping("/api/v1/company")
public class CompanyController {

    private final CompanyService company;

    public CompanyController(CompanyService company) {
        this.company = company;
    }

    /** Answers 404 until the company is configured with PUT. */
    @GetMapping
    public CompanyView get() {
        return company.get();
    }

    @PutMapping
    @CentralOnly
    public CompanyView configure(@Valid @RequestBody CompanyRequest request) {
        return company.configure(request.toData());
    }

    /** Currency and time zone format are checked by the domain. */
    public record CompanyRequest(@NotBlank @Size(max = 200) String legalName,
                                 @Size(max = 200) String tradeName,
                                 @NotBlank @Size(max = 14) String taxId,
                                 @Size(max = 300) String address,
                                 @Size(max = 20) String phone,
                                 @Email @Size(max = 150) String email,
                                 @NotBlank String currency,
                                 @NotBlank @Size(max = 50) String timeZone) {

        CompanyData toData() {
            return new CompanyData(legalName, tradeName, taxId, address, phone, email, currency, timeZone);
        }
    }
}
