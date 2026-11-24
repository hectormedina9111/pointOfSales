package com.medinasystems.pos.catalog.domain;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/** A product category. Categories form a tree through {@code parent}. */
@Entity
@Table(name = "category")
public class Category extends AuditableEntity {

    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String name, Category parent, boolean active) {
        this.name = Objects.requireNonNull(name, "name is required");
        changeParent(parent);
        this.active = active;
    }

    /**
     * Changes the category. {@code inUse} tells whether it still has active subcategories or
     * active products; the caller computes it because that needs the repositories.
     */
    public void update(String name, Category newParent, boolean active, boolean inUse) {
        Objects.requireNonNull(name, "name is required");
        if (!active && inUse) {
            throw new BusinessRuleException("CATEGORY_IN_USE",
                    "The category has active subcategories or active products");
        }
        changeParent(newParent);
        this.name = name;
        this.active = active;
    }

    /** Walks up from the new parent: if this category shows up, the change would create a cycle. */
    private void changeParent(Category newParent) {
        for (Category ancestor = newParent; ancestor != null; ancestor = ancestor.parent) {
            boolean self = ancestor == this || (getId() != null && getId().equals(ancestor.getId()));
            if (self) {
                throw new BusinessRuleException("CATEGORY_CYCLE", "A category cannot be its own ancestor");
            }
        }
        this.parent = newParent;
    }

    public Category getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }
}
