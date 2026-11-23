package com.medinasystems.pos.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medinasystems.pos.TestcontainersConfiguration;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
class UserManagementTest {

    private static final String PASSWORD = "Usermgmt-Test-123";
    private static final String ADMIN = "usermgmt-admin";
    private static final String CASHIER = "usermgmt-cashier";
    private static final String INITIAL_PASSWORD = "Initial-Pass-123";
    private static final String RESET_PASSWORD = "Reset-Pass-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        persistUser(ADMIN, "ADMIN");
        persistUser(CASHIER, "CASHIER");
    }

    @Test
    void adminCreatesUserWhoMustChangeThePassword() throws Exception {
        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(accessToken(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody("usermgmt-create", "usermgmt-create@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("usermgmt-create"))
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.roles[0]").value("CASHIER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void cashierCannotCreateUsers() throws Exception {
        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(accessToken(CASHIER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody("usermgmt-denied", "usermgmt-denied@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void duplicatedUsernameIsRejectedWithBusinessCode() throws Exception {
        String admin = accessToken(ADMIN);
        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody("usermgmt-dup", "usermgmt-dup@example.com")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody("usermgmt-dup", "usermgmt-other@example.com")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("user.username.duplicated"));
    }

    @Test
    void passwordResetRevokesRefreshTokensAndForcesChangeAtNextLogin() throws Exception {
        String admin = accessToken(ADMIN);
        ResultActions created = mockMvc.perform(post("/api/v1/users").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newUserBody("usermgmt-reset", "usermgmt-reset@example.com")))
                .andExpect(status().isCreated());
        Number id = JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");

        String login = login("usermgmt-reset", INITIAL_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String oldRefresh = JsonPath.read(login, "$.refreshToken");

        mockMvc.perform(post("/api/v1/users/{id}/password-reset", id.longValue())
                        .header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"%s\"}".formatted(RESET_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(oldRefresh)))
                .andExpect(status().isUnauthorized());
        login("usermgmt-reset", INITIAL_PASSWORD).andExpect(status().isUnauthorized());
        login("usermgmt-reset", RESET_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String accessToken(String username) throws Exception {
        String body = login(username, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String newUserBody(String username, String email) {
        return """
                {"username":"%s","fullName":"Test %s","email":"%s","externalCode":"ERP-%s",
                 "roleCodes":["CASHIER"],"initialPassword":"%s"}
                """.formatted(username, username, email, username, INITIAL_PASSWORD);
    }

    private void persistUser(String username, String roleCode) {
        if (users.findByUsername(username).isPresent()) {
            return;
        }
        String hash = passwordEncoder.encode(PASSWORD);
        AppUser user = new AppUser(username, hash, username);
        user.changePassword(hash);
        user.addRole(roles.findByCode(roleCode).orElseThrow());
        users.save(user);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
