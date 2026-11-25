package com.medinasystems.pos.sales.web;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sales.application.CreditNoteFilter;
import com.medinasystems.pos.sales.application.CreditNoteService;
import com.medinasystems.pos.sales.application.CreditNoteView;
import com.medinasystems.pos.sales.domain.CreditNoteStatus;
import com.medinasystems.pos.shared.web.PageResponse;

/** Credit notes of the node: a list with filters and paging, and one note. Read only. */
@RestController
@RequestMapping("/api/v1/credit-notes")
public class CreditNoteController {

    private final CreditNoteService notes;

    public CreditNoteController(CreditNoteService notes) {
        this.notes = notes;
    }

    /** Notes newest first. {@code search} matches the number; {@code from} and {@code to} bound the issue time. */
    @GetMapping
    public PageResponse<CreditNoteView> list(@RequestParam(required = false) Long customerId,
                                             @RequestParam(required = false) CreditNoteStatus status,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) Long branchId,
                                             @RequestParam(required = false) Instant from,
                                             @RequestParam(required = false) Instant to,
                                             @PageableDefault(sort = {"issuedAt", "id"},
                                                     direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(notes.list(new CreditNoteFilter(customerId, status, search, branchId, from, to),
                pageable));
    }

    @GetMapping("/{id}")
    public CreditNoteView get(@PathVariable Long id) {
        return notes.get(id);
    }
}
