package com.medinasystems.pos.catalog.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.AttributeDefinition;
import com.medinasystems.pos.catalog.CategoryAttributes;
import com.medinasystems.pos.catalog.domain.AttributeValues;
import com.medinasystems.pos.catalog.infrastructure.CategoryAttributeRepository;

/** Answers {@link CategoryAttributes} from the template table. */
@Component
public class DefaultCategoryAttributes implements CategoryAttributes {

    private final CategoryAttributeRepository attributes;

    public DefaultCategoryAttributes(CategoryAttributeRepository attributes) {
        this.attributes = attributes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttributeDefinition> forCategory(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        return attributes.findByCategoryIdAndActiveTrueOrderBySortOrderAscIdAsc(categoryId).stream()
                .map(attribute -> new AttributeDefinition(attribute.getCode(), attribute.getName(),
                        attribute.getDataType().name(), attribute.getOptions(), attribute.isRequired()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> validate(Long categoryId, Map<String, ?> values) {
        return AttributeValues.normalize(forCategory(categoryId), values);
    }
}
