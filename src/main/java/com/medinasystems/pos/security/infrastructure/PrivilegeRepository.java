package com.medinasystems.pos.security.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.security.domain.Privilege;
import com.medinasystems.pos.security.domain.PrivilegeType;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {

    Optional<Privilege> findByCode(String code);

    List<Privilege> findByCodeIn(Collection<String> codes);

    List<Privilege> findByType(PrivilegeType type, Sort sort);

    Optional<Privilege> findByHttpMethodAndPathPattern(String httpMethod, String pathPattern);

    /** True when one of the user's roles grants the active endpoint privilege. */
    @Query(value = """
            select exists (select 1 from privilege p
                join role_privilege rp on rp.privilege_id = p.id
                join user_role ur on ur.role_id = rp.role_id
                where ur.user_id = :userId and p.type = 'ENDPOINT' and p.active
                  and p.http_method = :httpMethod and p.path_pattern = :pathPattern)
            """, nativeQuery = true)
    boolean existsForUser(@Param("userId") Long userId, @Param("httpMethod") String httpMethod,
                          @Param("pathPattern") String pathPattern);

    /** Distinct codes of the active privileges of the given types that the user holds. */
    @Query(value = """
            select distinct p.code from privilege p
                join role_privilege rp on rp.privilege_id = p.id
                join user_role ur on ur.role_id = rp.role_id
            where ur.user_id = :userId and p.active and p.type in (:types)
            order by p.code
            """, nativeQuery = true)
    List<String> findCodesForUser(@Param("userId") Long userId, @Param("types") Collection<String> types);

    /** Active privileges of one type that the user holds through any role, in menu order. */
    @Query("""
            select distinct p from AppUser u join u.roles r join r.privileges p
            where u.id = :userId and p.type = :type and p.active = true
            order by p.sortOrder, p.code
            """)
    List<Privilege> findActiveForUser(@Param("userId") Long userId, @Param("type") PrivilegeType type);
}
