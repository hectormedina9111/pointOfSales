package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.TransferDirection;
import com.medinasystems.pos.inventory.domain.TransferStatus;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferSpecifications;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Reads of the transfers: the list, paged and filtered in the database, and one transfer with its lines. A node sees the
 * transfers that involve a branch it sees; the central node sees all of them.
 */
@Service
@Transactional(readOnly = true)
public class InventoryTransferQueryService {

    private static final Set<String> TRANSFER_SORT = Set.of("sentAt", "number", "status", "id");

    private final InventoryTransferRepository transfers;
    private final InventoryDocumentRepository documents;
    private final BranchScope scope;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryBranches branches;

    public InventoryTransferQueryService(InventoryTransferRepository transfers, InventoryDocumentRepository documents,
                                         BranchScope scope, BranchDirectory branchDirectory, UserDirectory users,
                                         SaleableProducts products, InventoryBranches branches) {
        this.transfers = transfers;
        this.documents = documents;
        this.scope = scope;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
        this.branches = branches;
    }

    /**
     * The transfers of the visible branches. With {@code branchId}, the list is about that branch and {@code direction} picks
     * the transfers it sent or receives; without a direction both. A store only sees its own branch, so another branch gives an
     * empty page.
     */
    public Page<TransferSummaryView> transfers(Long branchId, TransferDirection direction, TransferStatus status,
                                               String search, Instant from, Instant to, Pageable pageable) {
        InventorySorting.requireSortable(pageable.getSort(), TRANSFER_SORT);
        Long filterBranch;
        if (scope.allBranches()) {
            filterBranch = branchId;
        } else {
            var own = scope.nodeBranchId();
            if (own.isEmpty() || (branchId != null && !branchId.equals(own.get()))) {
                return Page.empty(pageable);
            }
            filterBranch = own.get();
        }
        InventoryNames names = names();
        return transfers.findAll(InventoryTransferSpecifications.matching(filterBranch, direction, status, search, from, to),
                pageable).map(transfer -> TransferSummaryView.of(transfer, names));
    }

    /** One transfer with its lines. Answers 404 when no visible branch takes part in it. */
    public TransferView transfer(long id) {
        return transfers.findById(id)
                .filter(this::visible)
                .map(transfer -> TransferView.of(transfer, names()))
                .orElseThrow(() -> new ResourceNotFoundException("inventory transfer", id));
    }

    /** The active branches a transfer can go to, and whether this node operates each of them. */
    public List<TransferBranchView> branches() {
        return branchDirectory.activeBranches().stream()
                .map(info -> new TransferBranchView(info.branchId(), info.code(), info.name(),
                        branches.operates(info.branchId())))
                .toList();
    }

    private boolean visible(InventoryTransfer transfer) {
        return scope.canSee(transfer.getOriginBranchId()) || scope.canSee(transfer.getDestinationBranchId());
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents, transfers);
    }
}
