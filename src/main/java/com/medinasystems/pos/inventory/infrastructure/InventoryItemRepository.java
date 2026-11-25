package com.medinasystems.pos.inventory.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.medinasystems.pos.inventory.domain.InventoryItem;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long>, JpaSpecificationExecutor<InventoryItem> {

    Optional<InventoryItem> findByProductIdAndBranchId(long productId, long branchId);

    /** The stock rows of the given products in one branch; products without a row are not returned. */
    List<InventoryItem> findByBranchIdAndProductIdIn(long branchId, Collection<Long> productIds);

    /**
     * Creates the stock row of a product in a branch when it does not exist yet. ON CONFLICT DO NOTHING makes it safe
     * when two movements race: the second insert waits for the first transaction and then does nothing. Returns the rows
     * inserted (0 or 1).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO inventory_item (product_id, branch_id, quantity, average_cost, product_sku, product_name,"
            + " category_id, min_stock, last_movement_at, created_at, created_by, updated_at, updated_by, version)"
            + " VALUES (:productId, :branchId, 0, 0, :sku, :name, :categoryId, :minStock, NULL, :now, :by, :now, :by, 0)"
            + " ON CONFLICT (product_id, branch_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("productId") long productId, @Param("branchId") long branchId,
                       @Param("sku") String sku, @Param("name") String name, @Param("categoryId") Long categoryId,
                       @Param("minStock") BigDecimal minStock, @Param("now") Instant now, @Param("by") String by);

    /** The stock row, locked until the end of the transaction (SELECT ... FOR UPDATE). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId = :productId and i.branchId = :branchId")
    Optional<InventoryItem> findForUpdate(@Param("productId") long productId, @Param("branchId") long branchId);
}
