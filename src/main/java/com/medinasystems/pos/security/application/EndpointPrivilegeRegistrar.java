package com.medinasystems.pos.security.application;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;
import com.medinasystems.pos.security.domain.Role;
import com.medinasystems.pos.security.infrastructure.PrivilegeRepository;
import com.medinasystems.pos.security.infrastructure.RoleRepository;

/**
 * Central node, at every startup: creates an ENDPOINT privilege for each controller route that lacks one,
 * then grants every ENDPOINT privilege to ADMIN.
 *
 * <p>Central only: privileges and roles are master data that the stores receive by replication. If a store
 * created its own rows, their ids would differ from the central ones and the replicated rows would collide on
 * the unique code.
 */
@Component
@Profile("central")
public class EndpointPrivilegeRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EndpointPrivilegeRegistrar.class);
    private static final String ADMIN_CODE = "ADMIN";

    private final RequestMappingHandlerMapping handlerMapping;
    private final PrivilegeRepository privileges;
    private final RoleRepository roles;

    public EndpointPrivilegeRegistrar(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
                                      PrivilegeRepository privileges, RoleRepository roles) {
        this.handlerMapping = handlerMapping;
        this.privileges = privileges;
        this.roles = roles;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            for (String pattern : info.getPatternValues()) {
                if (!EndpointPrivileges.requiresPrivilege(pattern)) {
                    continue;
                }
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    if (privileges.findByHttpMethodAndPathPattern(method.name(), pattern).isEmpty()) {
                        privileges.save(newEndpoint(method.name(), pattern, entry.getValue()));
                        created++;
                    }
                }
            }
        }

        Role admin = roles.findByCode(ADMIN_CODE)
                .orElseThrow(() -> new IllegalStateException("Role ADMIN is missing; check migration V2"));
        List<Privilege> endpoints = privileges.findByType(PrivilegeType.ENDPOINT, Sort.by("id"));
        endpoints.forEach(admin::grant);
        log.info("Endpoint privileges: {} registered, {} granted to {}", created, endpoints.size(), ADMIN_CODE);
    }

    private Privilege newEndpoint(String httpMethod, String pattern, HandlerMethod handler) {
        String name = handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName();
        return Privilege.endpoint(freeCode(EndpointPrivileges.code(httpMethod, pattern)), httpMethod, pattern,
                EndpointPrivileges.truncate(name, EndpointPrivileges.NAME_MAX), EndpointPrivileges.module(pattern));
    }

    /** Two routes can produce the same code (for example "/a-b" and "/a_b"); the second gets a numeric suffix. */
    private String freeCode(String base) {
        String candidate = base;
        for (int n = 2; privileges.findByCode(candidate).isPresent(); n++) {
            candidate = EndpointPrivileges.truncate(base, EndpointPrivileges.CODE_MAX - 3) + "_" + n;
        }
        return candidate;
    }
}
