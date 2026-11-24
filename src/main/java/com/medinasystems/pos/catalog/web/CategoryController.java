package com.medinasystems.pos.catalog.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.catalog.application.CategoryData;
import com.medinasystems.pos.catalog.application.CategoryService;
import com.medinasystems.pos.catalog.application.CategoryView;
import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;

/** Product categories. DELETE deactivates the category. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categories;

    public CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public PageResponse<CategoryView> list(@RequestParam(required = false) Long parentId,
                                           @RequestParam(required = false) Boolean active,
                                           @PageableDefault(sort = "name") Pageable pageable) {
        return PageResponse.from(categories.list(parentId, active, pageable));
    }

    @GetMapping("/{id}")
    public CategoryView get(@PathVariable Long id) {
        return categories.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CentralOnly
    public CategoryView create(@Valid @RequestBody CategoryRequest request) {
        return categories.create(request.toData());
    }

    @PutMapping("/{id}")
    @CentralOnly
    public CategoryView update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request.toData());
    }

    @DeleteMapping("/{id}")
    @CentralOnly
    public CategoryView deactivate(@PathVariable Long id) {
        return categories.deactivate(id);
    }

    /** A null parent makes a root category; a missing active flag means active. */
    public record CategoryRequest(@NotBlank @Size(max = 100) String name,
                                  Long parentId,
                                  Boolean active) {

        CategoryData toData() {
            return new CategoryData(name, parentId, active == null || active);
        }
    }
}
