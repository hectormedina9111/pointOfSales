package com.medinasystems.pos.catalog.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.catalog.application.CategoryAttributeData;
import com.medinasystems.pos.catalog.application.CategoryAttributeService;
import com.medinasystems.pos.catalog.application.CategoryAttributeView;
import com.medinasystems.pos.catalog.domain.AttributeDataType;
import com.medinasystems.pos.shared.CentralOnly;

/** The characteristic template of a category. Reads are open to every node; writes are central only. */
@RestController
@RequestMapping("/api/v1/categories/{id}/attributes")
public class CategoryAttributeController {

    private final CategoryAttributeService attributes;

    public CategoryAttributeController(CategoryAttributeService attributes) {
        this.attributes = attributes;
    }

    @GetMapping
    public List<CategoryAttributeView> list(@PathVariable long id) {
        return attributes.list(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public CategoryAttributeView create(@PathVariable long id, @Valid @RequestBody AttributeRequest request) {
        return attributes.create(id, request.toData());
    }

    @PutMapping("/{attributeId}")
    @CentralOnly
    public CategoryAttributeView update(@PathVariable long id, @PathVariable long attributeId,
                                        @Valid @RequestBody AttributeRequest request) {
        return attributes.update(id, attributeId, request.toData());
    }

    @DeleteMapping("/{attributeId}")
    @CentralOnly
    public CategoryAttributeView deactivate(@PathVariable long id, @PathVariable long attributeId) {
        return attributes.deactivate(id, attributeId);
    }

    /** A missing sort order is 0 and a missing active flag means active. Options apply to LIST only. */
    public record AttributeRequest(@NotBlank @Size(max = 40) String code,
                                   @NotBlank @Size(max = 80) String name,
                                   @NotNull AttributeDataType dataType,
                                   List<@NotBlank @Size(max = 100) String> options,
                                   Boolean required,
                                   Integer sortOrder,
                                   Boolean active) {

        CategoryAttributeData toData() {
            return new CategoryAttributeData(code, name, dataType, options, required != null && required,
                    sortOrder == null ? 0 : sortOrder, active == null || active);
        }
    }
}
