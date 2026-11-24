package com.medinasystems.pos.audit.web;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
class AuditLogTest {

    private static final String PASSWORD = "Audit-Test-Pass-123";
    private static final String ADMIN = "audittest-admin";
    private static final String NO_PRIVILEGES = "audittest-none";
    private static final String NO_PRIVILEGES_ROLE = "ORG_NONE";

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
        if (roles.findByCode(NO_PRIVILEGES_ROLE).isEmpty()) {
            roles.save(new Role(NO_PRIVILEGES_ROLE, "Sin privilegios", null));
        }
        persistUser(NO_PRIVILEGES, NO_PRIVILEGES_ROLE);
        configureCompany(accessToken(ADMIN));
    }

    @Test
    void branchCreationIsRecordedWithUserAndNewValues() throws Exception {
        String admin = accessToken(ADMIN);
        long countryId = createCountry(admin, "AU");
        String code = randomCode();
        long branchId = createBranch(admin, countryId, code, "Centro");

        mockMvc.perform(get("/api/v1/audit-log").param("entity", "branch")
                        .param("entityId", String.valueOf(branchId))
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("BRANCH_CREATED"))
                .andExpect(jsonPath("$.content[0].entity").value("branch"))
                .andExpect(jsonPath("$.content[0].username").value(ADMIN))
                .andExpect(jsonPath("$.content[0].newValues.code").value(code))
                .andExpect(jsonPath("$.content[0].oldValues").doesNotExist());
    }

    @Test
    void branchChangeKeepsOnlyTheChangedFields() throws Exception {
        String admin = accessToken(ADMIN);
        long countryId = createCountry(admin, "BO");
        String code = randomCode();
        long branchId = createBranch(admin, countryId, code, "Original");

        mockMvc.perform(put("/api/v1/branches/{id}", branchId).header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody(code, countryId, "Renombrada")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-log").param("entity", "branch")
                        .param("entityId", String.valueOf(branchId))
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("BRANCH_UPDATED"))
                .andExpect(jsonPath("$.content[0].oldValues.name").value("Original"))
                .andExpect(jsonPath("$.content[0].newValues.name").value("Renombrada"))
                .andExpect(jsonPath("$.content[0].oldValues.code").doesNotExist());
    }

    @Test
    void userWithoutPrivilegeCannotReadTheAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/audit-log").header(AUTHORIZATION, bearer(accessToken(NO_PRIVILEGES))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
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

    private long createBranch(String admin, long countryId, String code, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/branches").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(branchBody(code, countryId, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private static String branchBody(String code, long countryId, String name) {
        return """
                {"code":"%s","name":"%s","countryId":%d,"allowNegativeStock":false,"active":true}
                """.formatted(code, name, countryId);
    }

    private static String randomCode() {
        return "A" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
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
