package com.medinasystems.pos.catalog.application;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.domain.Category;
import com.medinasystems.pos.catalog.infrastructure.CategoryRepository;
import com.medinasystems.pos.catalog.infrastructure.CategorySpecifications;
import com.medinasystems.pos.catalog.infrastructure.ProductRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** Product categories. Deactivating one is refused while it still has active children or products. */
@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ApplicationEventPublisher events;

    public CategoryService(CategoryRepository categories, ProductRepository products,
                           ApplicationEventPublisher events) {
        this.categories = categories;
        this.products = products;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<CategoryView> list(Long parentId, Boolean active, Pageable pageable) {
        return categories.findAll(CategorySpecifications.matching(parentId, active), pageable)
                .map(CategoryView::from);
    }

    @Transactional(readOnly = true)
    public CategoryView get(Long id) {
        return CategoryView.from(find(id));
    }

    public CategoryView create(CategoryData data) {
        Category category = new Category(data.name(), findParent(data.parentId()), data.active());
        Category saved = categories.save(category);
        events.publishEvent(AuditEvent.created("CATEGORY_CREATED", "category", saved.getId(), snapshot(saved)));
        return CategoryView.from(saved);
    }

    public CategoryView update(Long id, CategoryData data) {
        Category category = find(id);
        Map<String, Object> before = snapshot(category);
        Category parent = findParent(data.parentId());
        boolean inUse = !data.active() && hasActiveDependents(id);
        category.update(data.name(), parent, data.active(), inUse);
        events.publishEvent(AuditEvent.updated("CATEGORY_UPDATED", "category", id, before, snapshot(category)));
        return CategoryView.from(category);
    }

    /** DELETE does not remove the row: it deactivates the category, under the same rules as PUT. */
    public CategoryView deactivate(Long id) {
        Category category = find(id);
        Map<String, Object> before = snapshot(category);
        category.update(category.getName(), category.getParent(), false, hasActiveDependents(id));
        events.publishEvent(AuditEvent.updated("CATEGORY_DEACTIVATED", "category", id, before,
                snapshot(category)));
        return CategoryView.from(category);
    }

    private boolean hasActiveDependents(Long id) {
        return categories.existsByParentIdAndActiveTrue(id) || products.existsByCategoryIdAndActiveTrue(id);
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("category", id));
    }

    private Category findParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categories.findById(parentId)
                .orElseThrow(() -> new BusinessRuleException("CATEGORY_NOT_FOUND", "The parent category does not exist"));
    }

    private static Map<String, Object> snapshot(Category category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("parentId", category.getParent() == null ? null : category.getParent().getId());
        values.put("name", category.getName());
        values.put("active", category.isActive());
        return values;
    }
}
