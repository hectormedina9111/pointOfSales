package com.medinasystems.pos.catalog;

import java.util.List;
import java.util.Map;

/**
 * Public API of the catalog module for the inventory module: the characteristics that the equipment of a category must
 * carry. A null category has no template.
 */
public interface CategoryAttributes {

    /** The active characteristics of the category, in template order. */
    List<AttributeDefinition> forCategory(Long categoryId);

    /**
     * Checks the values against the template of the category.
     *
     * @return the normalized values, in template order
     * @throws com.medinasystems.pos.shared.BusinessRuleException UNKNOWN_ATTRIBUTE, ATTRIBUTE_REQUIRED or INVALID_ATTRIBUTE_VALUE
     */
    Map<String, Object> validate(Long categoryId, Map<String, ?> values);
}
