package com.medinasystems.pos.security.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.security.domain.RoleDefaultGrant;
import com.medinasystems.pos.security.domain.RoleDefaultGrantKey;

public interface RoleDefaultGrantRepository extends JpaRepository<RoleDefaultGrant, RoleDefaultGrantKey> {
}
