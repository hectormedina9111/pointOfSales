package com.medinasystems.pos.pricing.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.medinasystems.pos.pricing.domain.PriceListItem;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    Page<PriceListItem> findByPriceListId(Long priceListId, Pageable pageable);

    Page<PriceListItem> findByPriceListIdAndProductId(Long priceListId, Long productId, Pageable pageable);

    List<PriceListItem> findAllByPriceListIdAndProductId(Long priceListId, Long productId);

    Optional<PriceListItem> findByIdAndPriceListId(Long id, Long priceListId);

    boolean existsByPriceListId(Long priceListId);

    boolean existsByPriceListIdAndProductIdAndMinQuantity(Long priceListId, Long productId, BigDecimal minQuantity);

    boolean existsByPriceListIdAndProductIdAndMinQuantityAndIdNot(Long priceListId, Long productId,
                                                                  BigDecimal minQuantity, Long id);
}
