package com.medinasystems.pos.sales.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.sales.domain.CreditNote;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.infrastructure.CreditNoteRepository;
import com.medinasystems.pos.sales.infrastructure.CreditNoteSpecifications;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Reads the credit notes of the node. A store lists the notes of its branch, the central node all of them; a note the node
 * cannot see answers 404. Filters and paging are applied in the database.
 */
@Service
@Transactional(readOnly = true)
public class CreditNoteService {

    private final CreditNoteRepository notes;
    private final SaleRepository sales;
    private final CustomerDirectory customers;
    private final BranchScope scope;

    public CreditNoteService(CreditNoteRepository notes, SaleRepository sales, CustomerDirectory customers,
                             BranchScope scope) {
        this.notes = notes;
        this.sales = sales;
        this.customers = customers;
        this.scope = scope;
    }

    public CreditNoteView get(long id) {
        CreditNote note = notes.findById(id).filter(found -> scope.canSee(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("credit_note", id));
        return views(List.of(note)).get(0);
    }

    public Page<CreditNoteView> list(CreditNoteFilter filter, Pageable pageable) {
        Long branch = filter.branchId();
        if (!scope.allBranches()) {
            Optional<Long> node = scope.nodeBranchId();
            if (node.isEmpty() || (branch != null && !branch.equals(node.get()))) {
                return Page.empty(pageable);
            }
            branch = node.get();
        }
        Page<CreditNote> page = notes.findAll(CreditNoteSpecifications.matching(filter.withBranch(branch)), pageable);
        return new PageImpl<>(views(page.getContent()), pageable, page.getTotalElements());
    }

    /** The views of a set of notes, looking up each customer and each return once. */
    private List<CreditNoteView> views(List<CreditNote> found) {
        Map<Long, String> receipts = new HashMap<>();
        for (Sale ret : sales.findAllById(found.stream().map(CreditNote::getReturnSaleId).distinct().toList())) {
            receipts.put(ret.getId(), ret.getReceiptNumber());
        }
        Map<Long, String> names = new HashMap<>();
        for (CreditNote note : found) {
            names.computeIfAbsent(note.getCustomerId(),
                    id -> customers.find(id).map(CustomerInfo::displayName).orElseThrow(
                            () -> new ResourceNotFoundException("customer", id)));
        }
        return found.stream()
                .map(note -> CreditNoteView.from(note, names.get(note.getCustomerId()),
                        receipts.get(note.getReturnSaleId())))
                .toList();
    }
}
