package com.medinasystems.pos.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
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
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
class PrivilegeAuthorizationTest {

    private static final String PASSWORD = "Privilege-Test-123";
    private static final String ADMIN = "privauth-admin";
    private static final String CASHIER = "privauth-cashier";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PrivilegeRepository privileges;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        persistUser(ADMIN, "ADMIN");
        persistUser(CASHIER, "CASHIER");
    }

    /** The shared database keeps grants between tests, so CASHIER is emptied again. */
    @AfterEach
    void revokeCashierGrants() throws Exception {
        mockMvc.perform(put("/api/v1/roles/{id}/privileges", cashierRoleId())
                        .header(AUTHORIZATION, bearer(accessToken(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privilegeCodes\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void cashierIsForbiddenUntilAdminGrantsThePrivilege() throws Exception {
        String cashier = accessToken(CASHIER);
        mockMvc.perform(get("/api/v1/users").header(AUTHORIZATION, bearer(cashier)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Missing privilege for GET /api/v1/users"));

        String code = privileges.findByHttpMethodAndPathPattern("GET", "/api/v1/users")
                .orElseThrow().getCode();
        mockMvc.perform(put("/api/v1/roles/{id}/privileges", cashierRoleId())
                        .header(AUTHORIZATION, bearer(accessToken(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"privilegeCodes\":[\"%s\"]}".formatted(code)))
                .andExpect(status().isOk());

        // Same token as before: the grant applies at the next request, without a new login.
        mockMvc.perform(get("/api/v1/users").header(AUTHORIZATION, bearer(cashier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void menusShowOnlyGrantedMenusWithTheirGrantedChildren() throws Exception {
        String menus = body(ADMIN, "/api/v1/me/menus");
        List<String> adminChildren = JsonPath.read(menus, "$[?(@.code == 'MENU_ADMIN')].children[*].code");
        assertThat(adminChildren).containsExactly("MENU_ADMIN_USERS", "MENU_ADMIN_ROLES");

        mockMvc.perform(get("/api/v1/me/menus").header(AUTHORIZATION, bearer(accessToken(CASHIER))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void privilegeCodesIncludeActionsOfTheUser() throws Exception {
        List<String> codes = JsonPath.read(body(ADMIN, "/api/v1/me/privileges"), "$.codes");
        assertThat(codes).contains("ACTION_USER_PASSWORD_RESET", "GET_USERS");
    }

    private String body(String username, String path) throws Exception {
        return mockMvc.perform(get(path).header(AUTHORIZATION, bearer(accessToken(username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
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

    private long cashierRoleId() {
        return roles.findByCode("CASHIER").map(Role::getId).orElseThrow();
    }

    private String accessToken(String username) throws Exception {
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)));
        return JsonPath.read(login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
