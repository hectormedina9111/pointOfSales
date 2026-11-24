package com.medinasystems.pos.sync.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.sync.domain.StoreIdentity;

/** Store side only: the table holds at most one row. */
public interface StoreIdentityRepository extends JpaRepository<StoreIdentity, Long> {

    Optional<StoreIdentity> findFirstByOrderByIdAsc();
}
