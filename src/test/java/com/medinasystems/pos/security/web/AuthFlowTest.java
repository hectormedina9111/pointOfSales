package com.medinasystems.pos.security.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.medinasystems.pos.TestcontainersConfiguration;
import com.medinasystems.pos.security.domain.AppUser;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
@TestPropertySource(properties = "pos.security.admin-initial-password=Admin-Initial-123")
class AuthFlowTest {

    private static final String INITIAL_PASSWORD = "Admin-Initial-123";
    private static final String NEW_PASSWORD = "Admin-New-Password-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Walks the whole admin lifecycle in one test, so the shared admin state stays predictable. */
    @Test
    void adminMustChangeInitialPasswordAndTokensRotateAndRevoke() throws Exception {
        ResultActions first = login("admin", INITIAL_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        String access = read(first, "$.accessToken");

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/v1/system/info")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/does-not-exist").header(AUTHORIZATION, bearer(access)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Password change required"));
        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody("unknown-token")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/me/password").header(AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(INITIAL_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        login("admin", INITIAL_PASSWORD).andExpect(status().isUnauthorized());
        ResultActions second = login("admin", NEW_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
        String access2 = read(second, "$.accessToken");
        String refresh2 = read(second, "$.refreshToken");

        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, bearer(access2)))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        ResultActions rotated = refresh(refresh2).andExpect(status().isOk());
        String refresh3 = read(rotated, "$.refreshToken");
        assertThat(refresh3).isNotEqualTo(refresh2);

        refresh(refresh2)
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refresh3)))
                .andExpect(status().isNoContent());
        refresh(refresh3).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        mockMvc.perform(get("/api/v1/me").header(AUTHORIZATION, "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountIsLockedAfterFiveFailedLogins() throws Exception {
        String password = "Locked-Pass-123";
        String hash = passwordEncoder.encode(password);
        AppUser user = new AppUser("locked-user", hash, "Locked User");
        user.changePassword(hash);
        users.save(user);

        for (int i = 0; i < 5; i++) {
            login("locked-user", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("locked-user", password)
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.title").value("Account locked"));
    }

    @Test
    void passwordChangeRejectsShortAndReusedPasswords() throws Exception {
        String password = "Weak-Pass-123";
        users.save(new AppUser("weak-user", passwordEncoder.encode(password), "Weak User"));

        String access = read(login("weak-user", password), "$.accessToken");

        mockMvc.perform(post("/api/v1/me/password").header(AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(password, "short")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("password.too_short"));

        mockMvc.perform(post("/api/v1/me/password").header(AUTHORIZATION, bearer(access))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordBody(password, password)))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("password.same_as_current"));
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(refreshToken)));
    }

    private static String refreshBody(String refreshToken) {
        return "{\"refreshToken\":\"%s\"}".formatted(refreshToken);
    }

    private static String passwordBody(String current, String next) {
        return "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }
}
