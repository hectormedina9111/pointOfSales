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

import com.medinasystems.pos.organization.application.BranchData;
import com.medinasystems.pos.organization.application.BranchService;
import com.medinasystems.pos.organization.application.BranchView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/** Lists and changes only the branches the current user can see. */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branches;

    public BranchController(BranchService branches) {
        this.branches = branches;
    }

    @GetMapping
    public PageResponse<BranchView> list(@PageableDefault(sort = "code") Pageable pageable) {
        return PageResponse.from(branches.list(pageable));
    }

    @GetMapping("/{id}")
    public BranchView get(@PathVariable Long id) {
        return branches.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public BranchView create(@Valid @RequestBody BranchRequest request) {
        return branches.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public BranchView update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
        return branches.update(id, request.toData());
    }

    public record BranchRequest(@NotBlank @Size(max = 10) String code,
                                @NotBlank @Size(max = 120) String name,
                                @Size(max = 300) String address,
                                @Size(max = 20) String phone,
                                @NotNull Long countryId,
                                @NotNull Boolean allowNegativeStock,
                                @NotNull Boolean active,
                                Long defaultPriceListId,
                                Boolean acceptsForeignReturns) {

        BranchData toData() {
            return new BranchData(code, name, address, phone, countryId, allowNegativeStock, active,
                    defaultPriceListId, Boolean.TRUE.equals(acceptsForeignReturns));
        }
    }
}
