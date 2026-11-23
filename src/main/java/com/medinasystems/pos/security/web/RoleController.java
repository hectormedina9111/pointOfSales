package com.medinasystems.pos.security.web;

import java.util.List;

import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.security.application.PrivilegeView;
import com.medinasystems.pos.security.application.RoleService;
import com.medinasystems.pos.security.application.RoleView;

/**
 * Roles and their privileges. Removing ENDPOINT privileges from ADMIN is allowed, but the
 * startup registrar grants them back on the next start.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    public List<RoleView> list() {
        return roles.list();
    }

    @GetMapping("/{id}")
    public RoleView get(@PathVariable Long id) {
        return roles.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleView create(@Valid @RequestBody CreateRoleRequest request) {
        return roles.create(request.code(), request.name(), request.description());
    }

    @PutMapping("/{id}")
    public RoleView update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return roles.update(id, request.name(), request.description());
    }

    @GetMapping("/{id}/privileges")
    public List<PrivilegeView> privileges(@PathVariable Long id) {
        return roles.privileges(id);
    }

    @PutMapping("/{id}/privileges")
    public List<PrivilegeView> replacePrivileges(@PathVariable Long id,
                                                 @Valid @RequestBody ReplacePrivilegesRequest request) {
        return roles.replacePrivileges(id, request.privilegeCodes());
    }

    public record CreateRoleRequest(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z_]+") String code,
                                    @NotBlank @Size(max = 80) String name,
                                    @Size(max = 150) String description) {
    }

    public record UpdateRoleRequest(@NotBlank @Size(max = 80) String name,
                                    @Size(max = 150) String description) {
    }

    public record ReplacePrivilegesRequest(@NotNull List<@NotBlank String> privilegeCodes) {
    }
}
