package com.medinasystems.pos.sync.web;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.shared.web.PageResponse;
import com.medinasystems.pos.sync.application.ActivationCodeView;
import com.medinasystems.pos.sync.application.StoreNodeService;
import com.medinasystems.pos.sync.application.StoreNodeView;

/**
 * Registration of stores. Central node only: on a store every call, even a read, answers 403 READ_ONLY_NODE,
 * because the store list is the central node's own data.
 */
@RestController
@RequestMapping("/api/v1/store-nodes")
@CentralOnly
public class StoreNodeController {

    private final StoreNodeService stores;

    public StoreNodeController(StoreNodeService stores) {
        this.stores = stores;
    }

    @GetMapping
    public PageResponse<StoreNodeView> list(@PageableDefault(sort = "nodeNumber") Pageable pageable) {
        return PageResponse.from(stores.list(pageable));
    }

    @GetMapping("/{id}")
    public StoreNodeView get(@PathVariable Long id) {
        return stores.get(id);
    }

    /** The body is optional; without a branch the store is not tied to one. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreNodeView create(@RequestBody(required = false) CreateStoreNodeRequest request) {
        return stores.create(request == null ? null : request.branchId());
    }

    /** The code is shown only in this response. */
    @PostMapping("/{id}/activation-code")
    public ActivationCodeView activationCode(@PathVariable Long id) {
        return stores.generateActivationCode(id);
    }

    @PostMapping("/{id}/revoke")
    public StoreNodeView revoke(@PathVariable Long id) {
        return stores.revoke(id);
    }

    /** Sets the branch of the store, or clears it with a null branchId. The store picks it up on its next heartbeat. */
    @PutMapping("/{id}/branch")
    public StoreNodeView assignBranch(@PathVariable Long id, @RequestBody AssignBranchRequest request) {
        return stores.assignBranch(id, request.branchId());
    }

    public record CreateStoreNodeRequest(Long branchId) {
    }

    public record AssignBranchRequest(Long branchId) {
    }
}
