package com.medinasystems.pos.security.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

import com.medinasystems.pos.security.application.PrivilegeService;
import com.medinasystems.pos.security.application.PrivilegeService.Placement;
import com.medinasystems.pos.security.application.PrivilegeView;
import com.medinasystems.pos.security.domain.PrivilegeType;

/** Maintains MENU and ACTION privileges. ENDPOINT privileges can only be read. */
@RestController
@RequestMapping("/api/v1/privileges")
public class PrivilegeController {

    private final PrivilegeService privileges;

    public PrivilegeController(PrivilegeService privileges) {
        this.privileges = privileges;
    }

    @GetMapping
    public List<PrivilegeView> list(@RequestParam(required = false) PrivilegeType type) {
        return privileges.list(type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrivilegeView create(@Valid @RequestBody CreatePrivilegeRequest request) {
        return privileges.create(request.type(), request.code(), request.name(), request.description(),
                request.module(), new Placement(request.parentCode(), request.frontendRoute(), request.icon(),
                        request.sortOrder()));
    }

    @PutMapping("/{id}")
    public PrivilegeView update(@PathVariable Long id, @Valid @RequestBody UpdatePrivilegeRequest request) {
        return privileges.update(id, request.name(), request.description(),
                new Placement(request.parentCode(), request.frontendRoute(), request.icon(), request.sortOrder()),
                request.active());
    }

    public record CreatePrivilegeRequest(@NotNull PrivilegeType type,
                                         @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
                                         @NotBlank @Size(max = 120) String name,
                                         @Size(max = 300) String description,
                                         @NotBlank @Size(max = 30) String module,
                                         @Size(max = 80) String parentCode,
                                         @Size(max = 200) String frontendRoute,
                                         @Size(max = 50) String icon,
                                         @Min(0) @Max(32767) int sortOrder) {
    }

    /** For ENDPOINT privileges only name, description and active are applied. */
    public record UpdatePrivilegeRequest(@NotBlank @Size(max = 120) String name,
                                         @Size(max = 300) String description,
                                         @Size(max = 80) String parentCode,
                                         @Size(max = 200) String frontendRoute,
                                         @Size(max = 50) String icon,
                                         @NotNull @Min(0) @Max(32767) Integer sortOrder,
                                         @NotNull Boolean active) {
    }
}
