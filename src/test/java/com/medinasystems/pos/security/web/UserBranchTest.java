package com.medinasystems.pos.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

import com.jayway.jsonpath.JsonPath;
import com.medinasystems.pos.TestcontainersConfiguration;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
class UserBranchTest {

    private static final String PASSWORD = "Ubranch-Test-123";
    private static final String ADMIN = "ubtest-admin";
    private static final String TARGET = "ubtest-target";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() throws Exception {
        persistUser(ADMIN, "ADMIN");
        persistUser(TARGET, "CASHIER");
        configureCompany(accessToken(ADMIN));
    }

    @Test
    void adminAssignsBranchesToUserAndTheyAreReturned() throws Exception {
        String admin = accessToken(ADMIN);
        long countryId = createCountry(admin, "UB");
        long first = createBranch(admin, countryId);
        long second = createBranch(admin, countryId);
        long targetId = users.findByUsername(TARGET).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/{id}/branches", targetId).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchIds\":[%d,%d]}".formatted(first, second)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/users/{id}/branches", targetId)
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$");
        assertThat(ids.stream().map(Number::longValue).toList()).containsExactlyInAnyOrder(first, second);

        mockMvc.perform(get("/api/v1/users/{id}", targetId).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchIds.length()").value(2));
    }

    @Test
    void unknownBranchIdIsRejectedWithBranchNotFound() throws Exception {
        String admin = accessToken(ADMIN);
        long targetId = users.findByUsername(TARGET).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/{id}/branches", targetId).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchIds\":[999999999]}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    private void configureCompany(String admin) throws Exception {
        mockMvc.perform(put("/api/v1/company").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Medina Systems S.A.","taxId":"08019999123456",
                                 "currency":"HNL","timeZone":"America/Tegucigalpa"}
                                """))
                .andExpect(status().isOk());
    }

    private long createCountry(String admin, String isoCode) throws Exception {
        String body = mockMvc.perform(post("/api/v1/countries").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isoCode":"%s","name":"Pais de prueba","currency":"USD",
                                 "timeZone":"America/Tegucigalpa","taxIdLabel":"ID",
                                 "fiscalProvider":"NONE","active":true}
                                """.formatted(isoCode)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private long createBranch(String admin, long countryId) throws Exception {
        String code = "U" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        String body = mockMvc.perform(post("/api/v1/branches").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Sucursal %s","countryId":%d,
                                 "allowNegativeStock":false,"active":true}
                                """.formatted(code, code, countryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
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

    private String accessToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
