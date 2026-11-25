package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.inventory.StockLedger;
import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.domain.InventoryDocument;
import com.medinasystems.pos.inventory.domain.InventoryDocumentCounter;
import com.medinasystems.pos.inventory.domain.InventoryDocumentType;
import com.medinasystems.pos.inventory.domain.InventoryItem;
import com.medinasystems.pos.inventory.domain.StockCalculator;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentCounterRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.sync.Outbox;
import com.medinasystems.pos.inventory.application.InventoryDocumentData.LineData;

/**
 * Posts inventory documents: an entry, an exit or a count of one branch. The document is numbered under a lock on the
 * branch counter, then each line applies one movement through the {@link StockLedger}, in product order so that concurrent
 * documents lock the stock rows in the same order. A document that breaks a rule is rolled back as a whole.
 *
 * <p>A store also queues the document for the central node, in the same transaction. The central node receives the
 * document with its lines, movements and the resulting stock of each product.
 */
@Service
@Transactional
public class InventoryDocumentService {

    private static final String SYSTEM_AUDITOR = "system";

    private final InventoryDocumentRepository documents;
    private final InventoryDocumentCounterRepository counters;
    private final InventoryItemRepository items;
    private final StockLedger ledger;
    private final InventoryBranches branches;
    private final BranchScope scope;
    private final NodeProperties node;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final Outbox outbox;
    private final SerialTrail serialTrail;
    private final ApplicationEventPublisher events;
    private final AuditorAware<String> auditor;
    private final Clock clock;

    public InventoryDocumentService(InventoryDocumentRepository documents, InventoryDocumentCounterRepository counters,
                                    InventoryItemRepository items, StockLedger ledger, InventoryBranches branches,
                                    BranchScope scope, NodeProperties node, BranchDirectory branchDirectory,
                                    UserDirectory users, SaleableProducts products, Outbox outbox,
                                    SerialTrail serialTrail, ApplicationEventPublisher events,
                                    AuditorAware<String> auditor, Clock clock) {
        this.documents = documents;
        this.counters = counters;
        this.items = items;
        this.ledger = ledger;
        this.branches = branches;
        this.scope = scope;
        this.node = node;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
        this.outbox = outbox;
        this.serialTrail = serialTrail;
        this.events = events;
        this.auditor = auditor;
        this.clock = clock;
    }

    /**
     * Posts a document. A key already used returns the document that key created, and posts nothing.
     *
     * @throws BusinessRuleException with the code of the rule that failed; the document is not kept
     */
    public InventoryDocumentView post(InventoryDocumentData data) {
        long userId = CurrentUser.id();
        Optional<InventoryDocument> repeated = findRepeated(data.idempotencyKey());
        if (repeated.isPresent()) {
            return view(repeated.get());
        }
        long branchId = resolveBranch(data.branchId());
        BranchInfo branch = branches.operated(branchId);
        requireValid(data);

        // Serializes the documents of the branch and type: a second request with the same key waits here.
        InventoryDocumentCounter counter = lockCounter(branchId, data.type());
        repeated = findRepeated(data.idempotencyKey());
        if (repeated.isPresent()) {
            return view(repeated.get());
        }
        String number = data.type().prefix() + "-" + branch.code() + "-"
                + String.format(Locale.ROOT, "%06d", counter.next());
        Instant now = clock.instant();
        InventoryDocument document = documents.save(new InventoryDocument(number, data.type(), branchId,
                data.reason(), data.notes(), data.idempotencyKey(), now, userId));

        List<StockMovement> moved = new ArrayList<>();
        for (LineData line : sortedByProduct(data.lines())) {
            moved.addAll(applyLine(document, branchId, data.type(), line, userId));
        }
        if (document.getLines().isEmpty()) {
            throw new BusinessRuleException("ADJUSTMENT_WITHOUT_CHANGE", "No line changes the stock");
        }
        // Flushes the lines, so they have their ids for the upload.
        documents.saveAndFlush(document);
        if (node.isStore()) {
            outbox.enqueue(InventoryDocumentDocument.EVENT_TYPE, InventoryDocumentDocument.of(document, moved,
                    resultingItems(moved, branchId), serialTrail.of(moved)));
        }
        events.publishEvent(AuditEvent.created("INVENTORY_DOCUMENT_POSTED", "inventory_document", document.getId(),
                snapshot(document)));
        return view(document);
    }

    /**
     * Applies one line. An entry or an exit moves its quantity; an adjustment moves the stock to the counted quantity, and
     * a line that changes nothing is not kept. A serial adjustment can have two movements (units that left and units that
     * came back); the line keeps their net change.
     */
    private List<StockMovement> applyLine(InventoryDocument document, long branchId, InventoryDocumentType type,
                                          LineData line, long userId) {
        StockReferenceType reference = StockReferenceType.INVENTORY_DOCUMENT;
        if (type == InventoryDocumentType.ADJUSTMENT) {
            List<StockMovement> moved = ledger.count(branchId, line.productId(), line.countedQuantity(),
                    line.serialNumbers(), reference, document.getId(), userId);
            if (!moved.isEmpty()) {
                BigDecimal net = moved.stream().map(StockMovement::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
                document.addLine(line.productId(), net, scaled(line.countedQuantity()), moved.get(0).previousBalance(),
                        null);
            }
            return moved;
        }
        BigDecimal signed = type == InventoryDocumentType.EXIT ? line.quantity().negate() : line.quantity();
        StockMovement moved = ledger.apply(branchId, line.productId(), type.movementType(), signed,
                type == InventoryDocumentType.ENTRY ? line.unitCost() : null, line.serialNumbers(), reference,
                document.getId(), userId);
        document.addLine(moved.productId(), moved.quantity(), null, moved.previousBalance(), moved.unitCost());
        return List.of(moved);
    }

    /** The stock of each product the movements changed, as it is after the document. */
    private List<InventoryItem> resultingItems(List<StockMovement> moved, long branchId) {
        return ResultingStock.of(items, moved, branchId);
    }

    /**
     * The branch the document belongs to. A store posts for its own branch only; the central node needs the branch named.
     */
    private long resolveBranch(Long requested) {
        if (node.isStore()) {
            long own = scope.nodeBranchId().orElseThrow(() -> new BusinessRuleException("INVENTORY_BRANCH_NOT_OPERATED",
                    "This node does not operate the inventory of the branch"));
            if (requested != null && requested != own) {
                throw new BusinessRuleException("INVENTORY_BRANCH_NOT_OPERATED",
                        "This node does not operate the inventory of the branch");
            }
            return own;
        }
        if (requested == null) {
            throw new BusinessRuleException("INVENTORY_BRANCH_REQUIRED", "Choose the branch of the document");
        }
        return requested;
    }

    /** The rules that do not depend on the stock: the reason, the lines and the quantities that each type reads. */
    private static void requireValid(InventoryDocumentData data) {
        if (data.type().requiresReason() && (data.reason() == null || data.reason().isBlank())) {
            throw new BusinessRuleException("INVENTORY_REASON_REQUIRED", "The document needs a reason");
        }
        Set<Long> productIds = new HashSet<>();
        for (LineData line : data.lines()) {
            if (!productIds.add(line.productId())) {
                throw new BusinessRuleException("DUPLICATE_DOCUMENT_LINE", "A product appears twice in the document");
            }
            if (data.type() == InventoryDocumentType.ADJUSTMENT) {
                if (line.countedQuantity() == null || line.countedQuantity().signum() < 0) {
                    throw new BusinessRuleException("INVALID_COUNTED_QUANTITY", "The counted quantity cannot be negative");
                }
            } else if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
            }
            if (data.type() == InventoryDocumentType.ENTRY && line.unitCost() != null
                    && line.unitCost().signum() < 0) {
                throw new BusinessRuleException("NEGATIVE_AMOUNT", "The amount cannot be negative");
            }
        }
    }

    private static List<LineData> sortedByProduct(List<LineData> lines) {
        return lines.stream().sorted(Comparator.comparingLong(LineData::productId)).toList();
    }

    private InventoryDocumentCounter lockCounter(long branchId, InventoryDocumentType type) {
        counters.insertIfAbsent(branchId, type.name(), clock.instant(),
                auditor.getCurrentAuditor().orElse(SYSTEM_AUDITOR));
        return counters.findForUpdate(branchId, type.name())
                .orElseThrow(() -> new IllegalStateException("The document counter of the branch is missing"));
    }

    private Optional<InventoryDocument> findRepeated(String idempotencyKey) {
        return idempotencyKey == null ? Optional.empty() : documents.findByIdempotencyKey(idempotencyKey);
    }

    private static BigDecimal scaled(BigDecimal quantity) {
        return quantity.setScale(StockCalculator.QUANTITY_SCALE, RoundingMode.UNNECESSARY);
    }

    private InventoryDocumentView view(InventoryDocument document) {
        return InventoryDocumentView.of(document, names());
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents);
    }

    private static Map<String, Object> snapshot(InventoryDocument document) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("number", document.getNumber());
        values.put("type", document.getType().name());
        values.put("branchId", document.getBranchId());
        values.put("lines", document.getLines().size());
        return values;
    }
}
