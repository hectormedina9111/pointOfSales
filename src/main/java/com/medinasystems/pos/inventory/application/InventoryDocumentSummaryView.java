package com.medinasystems.pos.inventory.application;

import java.time.Instant;

import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;

/** A row of the document list: the header of a document and how many lines it has. */
public record InventoryDocumentSummaryView(long id, String number, InventoryDocumentType type, long branchId,
                                           String branchName, String reason, Instant postedAt, long userId,
                                           String userName, int lineCount) {

    static InventoryDocumentSummaryView of(InventoryDocument document, InventoryNames names) {
        return new InventoryDocumentSummaryView(document.getId(), document.getNumber(), document.getType(),
                document.getBranchId(), names.branchName(document.getBranchId()), document.getReason(),
                document.getPostedAt(), document.getUserId(), names.userName(document.getUserId()),
                document.getLines().size());
    }
}
