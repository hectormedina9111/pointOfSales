package com.medinasystems.pos.organization.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.ResultActions;

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
class OrganizationTest {

    private static final String PASSWORD = "Org-Test-Pass-123";
    private static final String ADMIN = "orgtest-admin";
    private static final String OTHER_ADMIN = "orgtest-other";
    private static final String NO_PRIVILEGES = "orgtest-none";
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
        // Holds every endpoint privilege through ADMIN, but has no branch assigned.
        persistUser(OTHER_ADMIN, "ADMIN");
        persistRole(NO_PRIVILEGES_ROLE, "Sin privilegios");
        persistUser(NO_PRIVILEGES, NO_PRIVILEGES_ROLE);
        configureCompany(accessToken(ADMIN));
    }

    @Test
    void adminCreatesBranchAndSeesItInTheList() throws Exception {
        String admin = accessToken(ADMIN);
        long countryId = createCountry(admin, "HN");
        String code = randomCode();

        long branchId = createBranch(admin, countryId, code);

        assertThat(visibleBranchIds(admin)).contains(branchId);
        mockMvc.perform(get("/api/v1/branches/{id}", branchId).header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.countryId").value(countryId));
    }

    @Test
    void userWithoutAssignedBranchSeesNoBranchAndCannotReadOne() throws Exception {
        String admin = accessToken(ADMIN);
        long branchId = createBranch(admin, createCountry(admin, "GT"), randomCode());

        String other = accessToken(OTHER_ADMIN);
        mockMvc.perform(get("/api/v1/branches").header(AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/v1/branches/{id}", branchId).header(AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicatedBranchCodeIsRejected() throws Exception {
        String admin = accessToken(ADMIN);
        long countryId = createCountry(admin, "SV");
        String code = randomCode();
        createBranch(admin, countryId, code);

        postBranch(admin, countryId, code)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_BRANCH_CODE"));
    }

    @Test
    void duplicatedIsoCodeIsRejected() throws Exception {
        String admin = accessToken(ADMIN);
        createCountry(admin, "NI");

        postCountry(admin, "NI", "Nicaragua", "America/Managua")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_ISO_CODE"));
    }

    @Test
    void invalidTimeZoneIsRejected() throws Exception {
        postCountry(accessToken(ADMIN), "XT", "Pais de prueba", "Mars/Olympus")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));
    }

    @Test
    void branchCannotBeCreatedForInactiveCountry() throws Exception {
        String admin = accessToken(ADMIN);
        mockMvc.perform(post("/api/v1/countries").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(countryBody("CL", "Chile", "America/Santiago", false)))
                .andExpect(status().isCreated());
        long countryId = countryIdByIso(admin, "CL");

        postBranch(admin, countryId, randomCode())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("COUNTRY_INACTIVE"));
    }

    @Test
    void userWithoutPrivilegeCannotCreateCountries() throws Exception {
        mockMvc.perform(post("/api/v1/countries").header(AUTHORIZATION, bearer(accessToken(NO_PRIVILEGES)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(countryBody("PA", "Panama", "America/Panama", true)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private void configureCompany(String admin) throws Exception {
        mockMvc.perform(put("/api/v1/company").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"legalName":"Medina Systems S.A.","tradeName":"Medina","taxId":"08019999123456",
                                 "address":"Tegucigalpa","phone":"2222-0000","email":"info@example.com",
                                 "currency":"HNL","timeZone":"America/Tegucigalpa"}
                                """))
                .andExpect(status().isOk());
    }

    private long createCountry(String admin, String isoCode) throws Exception {
        mockMvc.perform(post("/api/v1/countries").header(AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(countryBody(isoCode, "Pais " + isoCode, "America/Tegucigalpa", true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isoCode").value(isoCode));
        return countryIdByIso(admin, isoCode);
    }

    private long countryIdByIso(String admin, String isoCode) throws Exception {
        String body = mockMvc.perform(get("/api/v1/countries").param("size", "200")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.content[?(@.isoCode == '%s')].id".formatted(isoCode));
        return ids.getFirst().longValue();
    }

    private long createBranch(String admin, long countryId, String code) throws Exception {
        String body = postBranch(admin, countryId, code)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private ResultActions postBranch(String admin, long countryId, String code) throws Exception {
        return mockMvc.perform(post("/api/v1/branches").header(AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"%s","name":"Sucursal %s","address":"Centro","phone":"2222-1111",
                         "countryId":%d,"allowNegativeStock":false,"active":true,"defaultPriceListId":null}
                        """.formatted(code, code, countryId)));
    }

    private ResultActions postCountry(String admin, String isoCode, String name, String timeZone) throws Exception {
        return mockMvc.perform(post("/api/v1/countries").header(AUTHORIZATION, bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(countryBody(isoCode, name, timeZone, true)));
    }

    private List<Long> visibleBranchIds(String admin) throws Exception {
        String body = mockMvc.perform(get("/api/v1/branches").param("size", "200")
                        .header(AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$.content[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    private static String countryBody(String isoCode, String name, String timeZone, boolean active) {
        return """
                {"isoCode":"%s","name":"%s","currency":"USD","timeZone":"%s",
                 "taxIdLabel":"ID","fiscalProvider":"NONE","active":%s}
                """.formatted(isoCode, name, timeZone, active);
    }

    private static String randomCode() {
        return "B" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private void persistRole(String code, String name) {
        if (roles.findByCode(code).isPresent()) {
            return;
        }
        roles.save(new Role(code, name, null));
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
