package com.medinasystems.pos.sync.infrastructure;

import java.time.Duration;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.application.SyncProtocol.AckRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.ChangesPage;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotPage;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotVersion;

/**
 * Store side only: calls the replication endpoints of the central node. The store always starts the call. The
 * store token travels in the {@code X-Store-Token} header and is never logged or put in a URL.
 */
@Component
@Profile("store")
public class CentralSyncClient {

    private static final String TOKEN_HEADER = "X-Store-Token";
    /** A dead connection must not block the sync thread for good. */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;

    public CentralSyncClient(NodeProperties node) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder().baseUrl(node.centralUrl()).requestFactory(factory).build();
    }

    /**
     * @throws org.springframework.web.client.RestClientException when the central node cannot be reached or refuses
     */
    public SnapshotVersion snapshotVersion(String token) {
        return restClient.get()
                .uri("/api/v1/sync/snapshot/version")
                .header(TOKEN_HEADER, token)
                .retrieve()
                .body(SnapshotVersion.class);
    }

    public SnapshotPage snapshot(String token, String table, long afterId, int limit) {
        return restClient.get()
                .uri("/api/v1/sync/snapshot?table={table}&afterId={afterId}&limit={limit}", table, afterId, limit)
                .header(TOKEN_HEADER, token)
                .retrieve()
                .body(SnapshotPage.class);
    }

    public ChangesPage changes(String token, long sinceVersion, int limit) {
        return restClient.get()
                .uri("/api/v1/sync/changes?sinceVersion={since}&limit={limit}", sinceVersion, limit)
                .header(TOKEN_HEADER, token)
                .retrieve()
                .body(ChangesPage.class);
    }

    public void ack(String token, long version) {
        restClient.post()
                .uri("/api/v1/sync/ack")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AckRequest(version))
                .retrieve()
                .toBodilessEntity();
    }

    public HeartbeatResponse heartbeat(String token, HeartbeatRequest request) {
        return restClient.post()
                .uri("/api/v1/sync/heartbeat")
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(HeartbeatResponse.class);
    }
}
