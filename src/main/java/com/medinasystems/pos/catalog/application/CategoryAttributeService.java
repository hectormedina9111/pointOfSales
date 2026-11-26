package com.medinasystems.pos.catalog.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.domain.Category;
import com.medinasystems.pos.catalog.domain.CategoryAttribute;
import com.medinasystems.pos.catalog.infrastructure.CategoryAttributeRepository;
import com.medinasystems.pos.catalog.infrastructure.CategoryRepository;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/** The characteristic templates of the categories. Only the central node writes them; they reach the stores by sync. */
@Service
@Transactional
public class CategoryAttributeService {

    private static final Pattern CODE = Pattern.compile("[a-z0-9_]{1,40}");

    private final CategoryRepository categories;
    private final CategoryAttributeRepository attributes;
    private final ApplicationEventPublisher events;

    public CategoryAttributeService(CategoryRepository categories, CategoryAttributeRepository attributes,
                                    ApplicationEventPublisher events) {
        this.categories = categories;
        this.attributes = attributes;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<CategoryAttributeView> list(long categoryId) {
        requireCategory(categoryId);
        return attributes.findByCategoryIdOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(CategoryAttributeView::from).toList();
    }

    public CategoryAttributeView create(long categoryId, CategoryAttributeData data) {
        Category category = requireCategory(categoryId);
        if (data.code() == null || !CODE.matcher(data.code()).matches()) {
            throw new BusinessRuleException("ATTRIBUTE_CODE_INVALID",
                    "The code can only contain lowercase letters, digits and underscores");
        }
        if (attributes.existsByCategoryIdAndCode(categoryId, data.code())) {
            throw new BusinessRuleException("DUPLICATE_ATTRIBUTE_CODE", "The characteristic code is already used",
                    data.code());
        }
        CategoryAttribute attribute = attributes.save(new CategoryAttribute(category, data.code(), data.name(),
                data.dataType(), data.options(), data.required(), data.sortOrder(), data.active()));
        events.publishEvent(AuditEvent.created("CATEGORY_ATTRIBUTE_CREATED", "category_attribute", attribute.getId(),
                snapshot(attribute)));
        return CategoryAttributeView.from(attribute);
    }

    public CategoryAttributeView update(long categoryId, long attributeId, CategoryAttributeData data) {
        CategoryAttribute attribute = find(categoryId, attributeId);
        Map<String, Object> before = snapshot(attribute);
        attribute.update(data.code(), data.dataType(), data.name(), data.options(), data.required(),
                data.sortOrder(), data.active());
        events.publishEvent(AuditEvent.updated("CATEGORY_ATTRIBUTE_UPDATED", "category_attribute", attributeId, before,
                snapshot(attribute)));
        return CategoryAttributeView.from(attribute);
    }

    /** DELETE does not remove the row: it deactivates the characteristic. Values already stored stay. */
    public CategoryAttributeView deactivate(long categoryId, long attributeId) {
        CategoryAttribute attribute = find(categoryId, attributeId);
        Map<String, Object> before = snapshot(attribute);
        attribute.update(attribute.getCode(), attribute.getDataType(), attribute.getName(), attribute.getOptions(),
                attribute.isRequired(), attribute.getSortOrder(), false);
        events.publishEvent(AuditEvent.updated("CATEGORY_ATTRIBUTE_DEACTIVATED", "category_attribute", attributeId,
                before, snapshot(attribute)));
        return CategoryAttributeView.from(attribute);
    }

    private CategoryAttribute find(long categoryId, long attributeId) {
        requireCategory(categoryId);
        CategoryAttribute attribute = attributes.findById(attributeId)
                .orElseThrow(() -> new ResourceNotFoundException("category_attribute", attributeId));
        if (attribute.getCategory().getId() != categoryId) {
            throw new ResourceNotFoundException("category_attribute", attributeId);
        }
        return attribute;
    }

    private Category requireCategory(long categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("category", categoryId));
    }

    private static Map<String, Object> snapshot(CategoryAttribute attribute) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", attribute.getCode());
        values.put("name", attribute.getName());
        values.put("dataType", attribute.getDataType().name());
        values.put("options", attribute.getOptions());
        values.put("required", attribute.isRequired());
        values.put("sortOrder", attribute.getSortOrder());
        values.put("active", attribute.isActive());
        return values;
    }
}
