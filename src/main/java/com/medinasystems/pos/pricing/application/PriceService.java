package com.medinasystems.pos.pricing.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.ProductPricingCatalog;
import com.medinasystems.pos.catalog.ProductPricingInfo;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchPricingInfo;
import com.medinasystems.pos.pricing.domain.PriceList;
import com.medinasystems.pos.pricing.domain.PriceListItem;
import com.medinasystems.pos.pricing.domain.PriceResolution;
import com.medinasystems.pos.pricing.domain.PriceResolver;
import com.medinasystems.pos.pricing.domain.TaxBreakdown;
import com.medinasystems.pos.pricing.infrastructure.PriceListItemRepository;
import com.medinasystems.pos.pricing.infrastructure.PriceListRepository;
import com.medinasystems.pos.security.BranchAccess;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Resolves the price of a product. The price list is the one given, or else the default list of the
 * branch. The country comes from the list or, without a list, from the branch.
 */
@Service
@Transactional(readOnly = true)
public class PriceService {

    private final PriceListRepository lists;
    private final PriceListItemRepository items;
    private final ProductPricingCatalog products;
    private final BranchDirectory branches;
    private final BranchAccess access;
    private final Clock clock;

    public PriceService(PriceListRepository lists, PriceListItemRepository items, ProductPricingCatalog products,
                        BranchDirectory branches, BranchAccess access, Clock clock) {
        this.lists = lists;
        this.items = items;
        this.products = products;
        this.branches = branches;
        this.access = access;
        this.clock = clock;
    }

    /**
     * @param priceListId list to use; when null, the default list of {@code branchId} is used
     * @param branchId    branch of the sale; must be visible to the current user when given
     * @param date        day the price applies to; today (UTC) when null
     */
    public ProductPriceView resolve(Long productId, Long priceListId, Long branchId, BigDecimal quantity,
                                    LocalDate date) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
        }
        BranchPricingInfo branch = branchId == null ? null : findVisibleBranch(branchId);
        if (priceListId == null && branch == null) {
            throw new BusinessRuleException("PRICE_CONTEXT_REQUIRED",
                    "Send a price list or a branch to resolve the price");
        }
        Long listId = priceListId != null ? priceListId : branch.defaultPriceListId();
        PriceList list = listId == null ? null
                : lists.findById(listId).orElseThrow(() -> new ResourceNotFoundException("price_list", listId));
        long countryId = list != null ? list.getCountryId() : branch.countryId();

        ProductPricingInfo product = products.find(productId, countryId)
                .orElseThrow(() -> new ResourceNotFoundException("product", productId));
        if (!product.active()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE", "The product is inactive");
        }

        LocalDate day = date == null ? LocalDate.now(clock) : date;
        List<PriceListItem> candidates = list == null ? List.of()
                : items.findAllByPriceListIdAndProductId(list.getId(), productId);
        PriceResolution price = PriceResolver.resolve(list, candidates, product.salePrice(), quantity, day);
        TaxBreakdown tax = TaxBreakdown.of(price.unitPrice(), product.taxRate());
        return new ProductPriceView(productId, price.priceListId(), price.source(), quantity, price.unitPrice(),
                product.taxCode(), product.taxRate(), tax.taxableBase(), tax.taxAmount(), day);
    }

    /** A branch outside the user's branches is reported as not found, as the branch module does. */
    private BranchPricingInfo findVisibleBranch(long branchId) {
        if (!access.branchIdsOf(access.currentUserId()).contains(branchId)) {
            throw new ResourceNotFoundException("branch", branchId);
        }
        return branches.find(branchId).orElseThrow(() -> new ResourceNotFoundException("branch", branchId));
    }
}
