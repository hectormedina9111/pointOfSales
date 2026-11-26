package com.medinasystems.pos.catalog.domain;

import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * One characteristic of the equipment of a category (the template). The code is the key that the equipment uses and
 * never changes; the data type never changes either, because the stored values depend on it.
 */
@Entity
@Table(name = "category_attribute")
public class CategoryAttribute extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    @Column(nullable = false, length = 40, updatable = false)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 10, updatable = false)
    private AttributeDataType dataType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected CategoryAttribute() {
    }

    public CategoryAttribute(Category category, String code, String name, AttributeDataType dataType,
                             List<String> options, boolean required, int sortOrder, boolean active) {
        this.category = Objects.requireNonNull(category, "category is required");
        this.code = Objects.requireNonNull(code, "code is required");
        this.dataType = Objects.requireNonNull(dataType, "dataType is required");
        this.name = Objects.requireNonNull(name, "name is required");
        this.options = normalizeOptions(dataType, options);
        this.required = required;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /**
     * Changes the characteristic. The code and the data type are fixed: a request that names another one is refused.
     *
     * @throws BusinessRuleException ATTRIBUTE_CODE_IMMUTABLE, ATTRIBUTE_TYPE_IMMUTABLE or ATTRIBUTE_OPTIONS_REQUIRED
     */
    public void update(String requestedCode, AttributeDataType requestedType, String name, List<String> options,
                       boolean required, int sortOrder, boolean active) {
        if (!this.code.equals(requestedCode)) {
            throw new BusinessRuleException("ATTRIBUTE_CODE_IMMUTABLE", "The code of a characteristic cannot change");
        }
        if (this.dataType != requestedType) {
            throw new BusinessRuleException("ATTRIBUTE_TYPE_IMMUTABLE",
                    "The data type of a characteristic cannot change");
        }
        this.name = Objects.requireNonNull(name, "name is required");
        this.options = normalizeOptions(dataType, options);
        this.required = required;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    /** Lists keep their options (trimmed, without blanks); the other types have none. */
    private static List<String> normalizeOptions(AttributeDataType type, List<String> options) {
        if (type != AttributeDataType.LIST) {
            return null;
        }
        List<String> values = options == null ? List.of() : options.stream()
                .filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        if (values.isEmpty()) {
            throw new BusinessRuleException("ATTRIBUTE_OPTIONS_REQUIRED", "A list characteristic needs its options");
        }
        return values;
    }

    public Category getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public AttributeDataType getDataType() {
        return dataType;
    }

    public List<String> getOptions() {
        return options;
    }

    public boolean isRequired() {
        return required;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
