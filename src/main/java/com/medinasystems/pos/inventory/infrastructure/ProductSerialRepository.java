package com.medinasystems.pos.inventory.infrastructure;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;

public interface ProductSerialRepository extends JpaRepository<ProductSerial, Long>, JpaSpecificationExecutor<ProductSerial> {

    /** The unit with this serial number of the product, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductSerial s where s.productId = :productId and s.serialNumber in :numbers")
    List<ProductSerial> findForUpdate(@Param("productId") long productId, @Param("numbers") Collection<String> numbers);

    /** The units of a product that are AVAILABLE in one branch, locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductSerial s where s.productId = :productId and s.branchId = :branchId"
            + " and s.status = :status")
    List<ProductSerial> findForUpdateInBranch(@Param("productId") long productId, @Param("branchId") long branchId,
                                              @Param("status") SerialStatus status);


    /** The units that carry one of these external codes. */
    List<ProductSerial> findByExternalCodeIn(Collection<String> codes);

    /** The units with these ids, in any order. */
    List<ProductSerial> findByIdIn(Collection<Long> ids);
}
