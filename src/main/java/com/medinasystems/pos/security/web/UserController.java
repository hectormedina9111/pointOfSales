package com.medinasystems.pos.security.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

import com.medinasystems.pos.security.application.UserService;
import com.medinasystems.pos.security.application.UserView;
import com.medinasystems.pos.shared.web.PageResponse;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    public PageResponse<UserView> list(@PageableDefault(sort = "username") Pageable pageable) {
        return PageResponse.from(users.list(pageable));
    }

    @GetMapping("/{id}")
    public UserView get(@PathVariable Long id) {
        return users.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request) {
        return users.create(request.username(), request.fullName(), request.email(), request.externalCode(),
                request.roleCodes(), request.initialPassword());
    }

    @PutMapping("/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request.fullName(), request.email(), request.externalCode(), request.active(),
                request.roleCodes());
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        users.resetPassword(id, request.newPassword());
    }

    public record CreateUserRequest(@NotBlank @Size(max = 50) String username,
                                    @NotBlank @Size(max = 150) String fullName,
                                    @Email @Size(max = 150) String email,
                                    @Size(max = 50) String externalCode,
                                    @NotNull List<@NotBlank String> roleCodes,
                                    @NotBlank @Size(min = 10, max = 72) String initialPassword) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName,
                                    @Email @Size(max = 150) String email,
                                    @Size(max = 50) String externalCode,
                                    @NotNull Boolean active,
                                    @NotNull List<@NotBlank String> roleCodes) {
    }

    public record PasswordResetRequest(@NotBlank @Size(min = 10, max = 72) String newPassword) {
    }
}
