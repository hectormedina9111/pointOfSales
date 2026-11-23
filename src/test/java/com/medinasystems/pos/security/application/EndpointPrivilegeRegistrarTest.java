package com.medinasystems.pos.security.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.TestcontainersConfiguration;
import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("central")
class EndpointPrivilegeRegistrarTest {

    @Autowired
    private PrivilegeRepository privileges;

    @Autowired
    private RoleRepository roles;

    @Test
    void registersControllerRoutesButNotPublicOrSelfServiceOnes() {
        assertThat(privileges.findByHttpMethodAndPathPattern("GET", "/api/v1/users")).isPresent();
        assertThat(privileges.findByHttpMethodAndPathPattern("PUT", "/api/v1/roles/{id}/privileges")).isPresent();
        assertThat(privileges.findByHttpMethodAndPathPattern("POST", "/api/v1/auth/login")).isEmpty();
        assertThat(privileges.findByHttpMethodAndPathPattern("GET", "/api/v1/me")).isEmpty();

        Privilege users = privileges.findByHttpMethodAndPathPattern("GET", "/api/v1/users").orElseThrow();
        assertThat(users.getType()).isEqualTo(PrivilegeType.ENDPOINT);
        assertThat(users.getCode()).isEqualTo("GET_USERS");
        assertThat(users.getModule()).isEqualTo("users");
    }

    @Test
    @Transactional
    void adminRoleHoldsEveryEndpointPrivilege() {
        Role admin = roles.findByCode("ADMIN").orElseThrow();
        assertThat(admin.getPrivileges())
                .containsAll(privileges.findByType(PrivilegeType.ENDPOINT, Sort.by("id")));
    }
}
