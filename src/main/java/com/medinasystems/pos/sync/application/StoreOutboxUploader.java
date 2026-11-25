package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.medinasystems.pos.sync.application.SyncProtocol.UploadItem;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResult;
import com.medinasystems.pos.sync.infrastructure.CentralSyncClient;
import com.medinasystems.pos.sync.infrastructure.SyncOutboxRepository;
import com.medinasystems.pos.sync.infrastructure.SyncOutboxRepository.PendingRow;

import tools.jackson.databind.json.JsonMapper;

/** Store side: sends the pending outbox rows to the central node and records its answers. */
@Service
@Profile("store")
public class StoreOutboxUploader {

    static final int BATCH_SIZE = 100;
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";

    private final SyncOutboxRepository outbox;
    private final CentralSyncClient client;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public StoreOutboxUploader(SyncOutboxRepository outbox, CentralSyncClient client, JsonMapper jsonMapper,
                               Clock clock) {
        this.outbox = outbox;
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * Uploads the oldest pending rows. Rows the central node does not answer stay pending.
     *
     * @throws RuntimeException when the central node cannot be reached or refuses; the rows stay pending and the
     *                          caller must not pull changes in this cycle
     */
    public void uploadPending(String token) {
        List<PendingRow> rows = outbox.findPending(BATCH_SIZE);
        if (rows.isEmpty()) {
            return;
        }
        List<UploadItem> items = rows.stream()
                .map(row -> new UploadItem(row.id(), row.eventType(), jsonMapper.readTree(row.payloadJson())))
                .toList();
        UploadResponse response;
        try {
            response = client.upload(token, new UploadRequest(items));
        } catch (RuntimeException ex) {
            outbox.recordFailedAttempt(rows.stream().map(PendingRow::id).toList(), ex.getClass().getSimpleName());
            throw ex;
        }
        Instant answeredAt = clock.instant();
        for (UploadResult result : response.results()) {
            if (ACCEPTED.equals(result.result()) || REJECTED.equals(result.result())) {
                outbox.markUploaded(result.outboxId(), result.result(), result.code(), answeredAt);
            }
        }
    }
}
