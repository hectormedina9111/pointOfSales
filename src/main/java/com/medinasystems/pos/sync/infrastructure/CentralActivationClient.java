package com.medinasystems.pos.sync.infrastructure;

import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.application.ActivationResult;

/** Store side only: calls the activation endpoint of the central node. The store always starts the call. */
@Component
@Profile("store")
public class CentralActivationClient {

    private final RestClient restClient;

    public CentralActivationClient(NodeProperties node) {
        this.restClient = RestClient.builder().baseUrl(node.centralUrl()).build();
    }

    /**
     * Exchanges the activation code for the store's credentials.
     *
     * @throws org.springframework.web.client.HttpClientErrorException when the central node rejects the code
     * @throws org.springframework.web.client.RestClientException when the central node cannot be reached
     */
    public ActivationResult activate(String activationCode) {
        return restClient.post()
                .uri("/api/v1/sync/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("activationCode", activationCode))
                .retrieve()
                .body(ActivationResult.class);
    }
}
