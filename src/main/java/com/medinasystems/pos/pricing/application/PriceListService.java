package com.medinasystems.pos.pricing.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.ProductPricingCatalog;
import com.medinasystems.pos.organization.CountryDirectory;
import com.medinasystems.pos.pricing.domain.PriceList;
import com.medinasystems.pos.pricing.domain.PriceListItem;
import com.medinasystems.pos.pricing.domain.PricingMethod;
import com.medinasystems.pos.pricing.infrastructure.PriceListItemRepository;
import com.medinasystems.pos.pricing.infrastructure.PriceListRepository;
import com.medinasystems.pos.pricing.infrastructure.PriceListSpecifications;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Price lists and their prices per product. Changes to lists and items are recorded in the audit log
 * with the old and new prices. Items are deleted for real: they are not catalogs.
 */
@Service
@Transactional
public class PriceListService {

    private final PriceListRepository lists;
    private final PriceListItemRepository items;
    private final ProductPricingCatalog products;
    private final CountryDirectory countries;
    private final ApplicationEventPublisher events;

    public PriceListService(PriceListRepository lists, PriceListItemRepository items,
                            ProductPricingCatalog products, CountryDirectory countries,
                            ApplicationEventPublisher events) {
        this.lists = lists;
        this.items = items;
        this.products = products;
        this.countries = countries;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<PriceListView> list(Long countryId, Boolean active, Pageable pageable) {
        return lists.findAll(PriceListSpecifications.matching(countryId, active), pageable)
                .map(PriceListView::from);
    }

    @Transactional(readOnly = true)
    public PriceListView get(Long id) {
        return PriceListView.from(find(id));
    }

    public PriceListView create(PriceListData data) {
        requireActiveCountry(data.countryId());
        requireUniqueCode(data.code(), null);
        PriceList list = new PriceList(data.code(), data.name(), data.countryId(), data.pricingMethod(),
                data.percent(), data.validFrom(), data.validTo(), data.priority(), data.active());
        PriceList saved = lists.save(list);
        events.publishEvent(AuditEvent.created("PRICE_LIST_CREATED", "price_list", saved.getId(), snapshot(saved)));
        return PriceListView.from(saved);
    }

    public PriceListView update(Long id, PriceListData data) {
        PriceList list = find(id);
        if (!Objects.equals(list.getCountryId(), data.countryId())) {
            requireActiveCountry(data.countryId());
        }
        requireUniqueCode(data.code(), id);
        Map<String, Object> before = snapshot(list);
        list.update(data.code(), data.name(), data.countryId(), data.pricingMethod(), data.percent(),
                data.validFrom(), data.validTo(), data.priority(), data.active(), items.existsByPriceListId(id));
        events.publishEvent(AuditEvent.updated("PRICE_LIST_UPDATED", "price_list", id, before, snapshot(list)));
        return PriceListView.from(list);
    }

    @Transactional(readOnly = true)
    public Page<PriceListItemView> listItems(Long listId, Long productId, Pageable pageable) {
        find(listId);
        Page<PriceListItem> page = productId == null
                ? items.findByPriceListId(listId, pageable)
                : items.findByPriceListIdAndProductId(listId, productId, pageable);
        return page.map(PriceListItemView::from);
    }

    public PriceListItemView addItem(Long listId, PriceListItemData data) {
        PriceList list = find(listId);
        // The constructor checks the minimum quantity and the price before any query runs.
        PriceListItem item = new PriceListItem(listId, data.productId(), data.minQuantity(), data.price());
        requireFixedPrice(list);
        requireProduct(data.productId(), list.getCountryId());
        if (items.existsByPriceListIdAndProductIdAndMinQuantity(listId, data.productId(), item.getMinQuantity())) {
            throw duplicatedScale();
        }
        PriceListItem saved = items.save(item);
        events.publishEvent(AuditEvent.created("PRICE_LIST_ITEM_CREATED", "price_list_item", saved.getId(),
                itemSnapshot(saved)));
        return PriceListItemView.from(saved);
    }

    /**
     * The item changes first, which validates it; the checks that depend on the new values run after.
     * Any rule violation rolls the whole change back.
     */
    public PriceListItemView updateItem(Long listId, Long itemId, PriceListItemData data) {
        PriceList list = find(listId);
        PriceListItem item = findItem(listId, itemId);
        Map<String, Object> before = itemSnapshot(item);
        item.update(data.productId(), data.minQuantity(), data.price());
        requireProduct(item.getProductId(), list.getCountryId());
        if (items.existsByPriceListIdAndProductIdAndMinQuantityAndIdNot(listId, item.getProductId(),
                item.getMinQuantity(), itemId)) {
            throw duplicatedScale();
        }
        events.publishEvent(AuditEvent.updated("PRICE_LIST_ITEM_UPDATED", "price_list_item", itemId, before,
                itemSnapshot(item)));
        return PriceListItemView.from(item);
    }

    public void deleteItem(Long listId, Long itemId) {
        find(listId);
        PriceListItem item = findItem(listId, itemId);
        events.publishEvent(new AuditEvent("PRICE_LIST_ITEM_DELETED", "price_list_item", itemId,
                itemSnapshot(item), null));
        items.delete(item);
    }

    private PriceList find(Long id) {
        return lists.findById(id).orElseThrow(() -> new ResourceNotFoundException("price_list", id));
    }

    private PriceListItem findItem(Long listId, Long itemId) {
        return items.findByIdAndPriceListId(itemId, listId)
                .orElseThrow(() -> new ResourceNotFoundException("price_list_item", itemId));
    }

    private void requireActiveCountry(Long countryId) {
        if (!countries.isActive(countryId)) {
            throw new BusinessRuleException("COUNTRY_INACTIVE", "The country does not exist or is inactive");
        }
    }

    private void requireUniqueCode(String code, Long id) {
        boolean taken = id == null ? lists.existsByCode(code) : lists.existsByCodeAndIdNot(code, id);
        if (taken) {
            throw new BusinessRuleException("DUPLICATE_PRICE_LIST_CODE", "The price list code is already in use");
        }
    }

    private void requireFixedPrice(PriceList list) {
        if (list.getPricingMethod() != PricingMethod.FIXED_PRICE) {
            throw new BusinessRuleException("ITEMS_REQUIRE_FIXED_PRICE",
                    "Only FIXED_PRICE lists can have prices per product");
        }
    }

    private void requireProduct(Long productId, Long countryId) {
        if (products.find(productId, countryId).isEmpty()) {
            throw new BusinessRuleException("PRODUCT_NOT_FOUND", "The product does not exist");
        }
    }

    private static BusinessRuleException duplicatedScale() {
        return new BusinessRuleException("DUPLICATE_PRICE_SCALE",
                "The product already has a price for this minimum quantity in this list");
    }

    private static Map<String, Object> snapshot(PriceList list) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", list.getCode());
        values.put("name", list.getName());
        values.put("countryId", list.getCountryId());
        values.put("pricingMethod", list.getPricingMethod().name());
        values.put("percent", list.getPercent());
        values.put("validFrom", list.getValidFrom() == null ? null : list.getValidFrom().toString());
        values.put("validTo", list.getValidTo() == null ? null : list.getValidTo().toString());
        values.put("priority", list.getPriority());
        values.put("active", list.isActive());
        return values;
    }

    private static Map<String, Object> itemSnapshot(PriceListItem item) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("priceListId", item.getPriceListId());
        values.put("productId", item.getProductId());
        values.put("minQuantity", item.getMinQuantity());
        values.put("price", item.getPrice());
        return values;
    }
}
