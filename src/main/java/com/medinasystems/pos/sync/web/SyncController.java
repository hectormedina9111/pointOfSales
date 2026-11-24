package com.medinasystems.pos.sync.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.shared.CentralOnly;
import com.medinasystems.pos.sync.application.ActivationResult;
import com.medinasystems.pos.sync.application.CentralActivationService;
import com.medinasystems.pos.sync.application.SyncStatusService;
import com.medinasystems.pos.sync.application.SyncStatusView;

/**
 * Sync endpoints. Activation is public (the code is the credential) and runs only on the central node.
 * Ping needs the store token. Status needs a user token and a privilege, and works on both nodes.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final CentralActivationService activation;
    private final SyncStatusService status;

    public SyncController(CentralActivationService activation, SyncStatusService status) {
        this.activation = activation;
        this.status = status;
    }

    @PostMapping("/activate")
    @CentralOnly
    public ActivationResult activate(@Valid @RequestBody ActivationRequest request) {
        return activation.activate(request.activationCode());
    }

    /** Proves the store token works. Returns the store node id that the filter found. */
    @GetMapping("/ping")
    @CentralOnly
    public PingResponse ping(@RequestAttribute(StoreTokenAuthenticationFilter.STORE_NODE_ATTRIBUTE) long storeNodeId) {
        return new PingResponse(storeNodeId);
    }

    @GetMapping("/status")
    public SyncStatusView status() {
        return status.status();
    }

    public record ActivationRequest(@NotBlank @Size(max = 64) String activationCode) {
    }

    public record PingResponse(long storeNodeId) {
    }
}
