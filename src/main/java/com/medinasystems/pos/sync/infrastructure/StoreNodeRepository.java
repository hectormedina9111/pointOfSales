package com.medinasystems.pos.sync.infrastructure;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.sync.domain.StoreNode;

public interface StoreNodeRepository extends JpaRepository<StoreNode, Long> {

    Optional<StoreNode> findByNodeNumber(short nodeNumber);

    Optional<StoreNode> findTopByOrderByNodeNumberDesc();

    Optional<StoreNode> findByActivationCodeHash(String activationCodeHash);

    Optional<StoreNode> findByCredentialHash(String credentialHash);

    boolean existsByBranchId(Long branchId);

    boolean existsByBranchIdAndIdNot(Long branchId, Long id);
}
