package com.medinasystems.pos.inventory.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.catalog.SaleableProduct;
import com.medinasystems.pos.catalog.SaleableProducts;
import com.medinasystems.pos.inventory.StockLedger;
import com.medinasystems.pos.inventory.StockMovement;
import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.inventory.application.ReceiptData.LineData;
import com.medinasystems.pos.inventory.domain.InventoryDocumentCounter;
import com.medinasystems.pos.inventory.domain.InventoryTransfer;
import com.medinasystems.pos.inventory.domain.InventoryTransferLine;
import com.medinasystems.pos.inventory.domain.InventoryTransferSerial;
import com.medinasystems.pos.inventory.domain.TransferStatus;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentCounterRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryDocumentRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryItemRepository;
import com.medinasystems.pos.inventory.infrastructure.InventoryTransferRepository;
import com.medinasystems.pos.inventory.infrastructure.ProductSerialRepository;
import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.NodeProperties;
import com.medinasystems.pos.shared.ResourceNotFoundException;
import com.medinasystems.pos.shared.SerialNumbers;
import com.medinasystems.pos.sync.Outbox;

/**
 * Sends and receives inventory transfers. The origin branch sends a transfer: each line takes its quantity out at the average
 * cost (TRANSFER_OUT) and keeps the serial numbers, which become IN_TRANSIT. On a store, the transfer is queued for the central
 * node in the same transaction. The destination branch receives it: each line takes in what arrived (TRANSFER_IN) at the cost it
 * left with, and what did not arrive is reported as a difference.
 *
 * <p>Only the node that operates a branch moves its stock: the origin sends and the destination receives. The central node
 * stores the uploads of the stores (see the upload handlers) and replicates the transfers to every store by the change log.
 * It must run inside a transaction: the stock rows are locked until it ends.
 */
@Service
@Transactional
public class InventoryTransferService {

    private static final String SYSTEM_AUDITOR = "system";
    /** The counter type of transfer numbers, in the same table as the document counters. */
    static final String COUNTER_TYPE = "TRANSFER";

    private final InventoryTransferRepository transfers;
    private final InventoryDocumentCounterRepository counters;
    private final InventoryItemRepository items;
    private final StockLedger ledger;
    private final InventoryBranches branches;
    private final BranchScope scope;
    private final NodeProperties node;
    private final BranchDirectory branchDirectory;
    private final UserDirectory users;
    private final SaleableProducts products;
    private final InventoryDocumentRepository documents;
    private final Outbox outbox;
    private final SerialTrail serialTrail;
    private final ApplicationEventPublisher events;
    private final AuditorAware<String> auditor;
    private final Clock clock;
    private final ProductSerialRepository serialUnits;
    private final EquipmentRules equipmentRules;

    public InventoryTransferService(InventoryTransferRepository transfers, InventoryDocumentCounterRepository counters,
                                    InventoryItemRepository items, StockLedger ledger, InventoryBranches branches,
                                    BranchScope scope, NodeProperties node, BranchDirectory branchDirectory,
                                    UserDirectory users, SaleableProducts products,
                                    InventoryDocumentRepository documents, Outbox outbox, SerialTrail serialTrail,
                                    ApplicationEventPublisher events, AuditorAware<String> auditor, Clock clock,
                                    ProductSerialRepository serialUnits, EquipmentRules equipmentRules) {
        this.transfers = transfers;
        this.counters = counters;
        this.items = items;
        this.ledger = ledger;
        this.branches = branches;
        this.scope = scope;
        this.node = node;
        this.branchDirectory = branchDirectory;
        this.users = users;
        this.products = products;
        this.documents = documents;
        this.outbox = outbox;
        this.serialTrail = serialTrail;
        this.events = events;
        this.auditor = auditor;
        this.clock = clock;
        this.serialUnits = serialUnits;
        this.equipmentRules = equipmentRules;
    }

    /**
     * Sends a transfer from the origin branch. A key already used returns the transfer that key created, and sends nothing.
     *
     * @throws BusinessRuleException with the code of the rule that failed; nothing is kept
     */
    public TransferView send(TransferData data) {
        long userId = CurrentUser.id();
        Optional<InventoryTransfer> repeated = findSendKey(data.idempotencyKey());
        if (repeated.isPresent()) {
            return view(repeated.get());
        }
        long origin = resolveOrigin(data.originBranchId());
        BranchInfo originBranch = branches.operated(origin);
        long destination = data.destinationBranchId();
        requireRoute(origin, destination);
        requireSendLines(data.lines());

        // Serializes the transfers of the origin branch: a second request with the same key waits here.
        InventoryDocumentCounter counter = lockCounter(origin);
        repeated = findSendKey(data.idempotencyKey());
        if (repeated.isPresent()) {
            return view(repeated.get());
        }
        String number = "TR-" + originBranch.code() + "-" + String.format(Locale.ROOT, "%06d", counter.next());
        Instant now = clock.instant();
        InventoryTransfer transfer = transfers.save(new InventoryTransfer(number, origin, destination, data.notes(),
                data.idempotencyKey(), now, userId));

        List<StockMovement> moved = new ArrayList<>();
        for (TransferData.LineData line : sortedByProduct(data.lines())) {
            List<String> serials = serialsToTrack(line.productId(), line.serialNumbers());
            StockMovement out = ledger.apply(origin, line.productId(), StockMovementType.TRANSFER_OUT,
                    line.quantity().negate(), null, serials, StockReferenceType.TRANSFER, transfer.getId(), userId);
            InventoryTransferLine created = transfer.addLine(line.productId(), out.quantity().abs(), out.unitCost());
            // The units carry their equipment data with them, so the destination can create the equipment.
            Map<String, ProductSerial> sent = unitsByNumber(line.productId(), serials);
            for (String serial : serials) {
                ProductSerial unit = sent.get(serial);
                created.addSerial(serial, unit == null ? null : unit.getExternalCode(),
                        unit == null ? Map.of() : unit.getAttributes());
            }
            moved.add(out);
        }
        transfers.saveAndFlush(transfer);
        if (node.isStore()) {
            outbox.enqueue(TransferSentDocument.EVENT_TYPE, TransferSentDocument.of(transfer, moved,
                    ResultingStock.of(items, moved, origin), serialTrail.of(moved), by()));
        }
        events.publishEvent(AuditEvent.created("INVENTORY_TRANSFER_SENT", "inventory_transfer", transfer.getId(),
                snapshot(transfer)));
        return view(transfer);
    }

    /**
     * Records the receipt of a transfer by its destination branch. A transfer already received with the same key returns the
     * transfer, and receives nothing. Every line must be answered once; the quantities received cannot exceed those sent.
     *
     * @throws BusinessRuleException TRANSFER_WRONG_BRANCH, TRANSFER_NOT_IN_TRANSIT, RECEIVED_EXCEEDS_SENT and the other rules
     */
    public TransferView receive(long transferId, ReceiptData data) {
        long userId = CurrentUser.id();
        InventoryTransfer transfer = transfers.findForUpdate(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory transfer", transferId));
        if (!branches.operates(transfer.getDestinationBranchId())) {
            throw new BusinessRuleException("TRANSFER_WRONG_BRANCH",
                    "This node does not receive the transfer: its destination is another branch");
        }
        String key = data.idempotencyKey();
        if (key != null && key.equals(transfer.getReceiveIdempotencyKey())) {
            return view(transfer);
        }
        if (transfer.getStatus() != TransferStatus.IN_TRANSIT) {
            throw new BusinessRuleException("TRANSFER_NOT_IN_TRANSIT", "The transfer is not in transit");
        }
        if (key != null && transfers.findByReceiveIdempotencyKey(key).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_IDEMPOTENCY_KEY",
                    "The idempotency key was already used by another transfer");
        }

        List<Receipt> receipts = receiptsOf(transfer, data.lines());
        for (Receipt receipt : receipts) {
            requireFreeExternalCodes(receipt);
        }
        List<StockMovement> moved = new ArrayList<>();
        for (Receipt receipt : receipts) {
            if (receipt.quantity().signum() > 0) {
                moved.add(ledger.apply(transfer.getDestinationBranchId(), receipt.line().getProductId(),
                        StockMovementType.TRANSFER_IN, receipt.quantity(), receipt.line().getUnitCost(),
                        receipt.serials(), StockReferenceType.TRANSFER, transfer.getId(), userId));
                applyReceivedEquipment(receipt);
            }
        }

        boolean differences = false;
        for (Receipt receipt : receipts) {
            InventoryTransferLine line = receipt.line();
            line.recordReceived(receipt.quantity());
            if (receipt.quantity().compareTo(line.getQuantity()) != 0) {
                differences = true;
            }
            for (InventoryTransferSerial serial : line.getSerials()) {
                boolean got = receipt.serials().contains(serial.getSerialNumber());
                serial.markReceived(got);
                differences |= !got;
            }
        }
        TransferStatus status = differences ? TransferStatus.RECEIVED_WITH_DIFFERENCES : TransferStatus.RECEIVED;
        transfer.receive(status, clock.instant(), userId, data.notes(), key);
        transfers.saveAndFlush(transfer);
        if (node.isStore()) {
            outbox.enqueue(TransferReceivedDocument.EVENT_TYPE, TransferReceivedDocument.of(transfer, moved,
                    ResultingStock.of(items, moved, transfer.getDestinationBranchId()), serialTrail.of(moved), by()));
        }
        events.publishEvent(AuditEvent.created("INVENTORY_TRANSFER_RECEIVED", "inventory_transfer", transfer.getId(),
                snapshot(transfer)));
        return view(transfer);
    }

    /**
     * The checked receipt of every line, in product order: the quantity received and its serial numbers, each among the ones
     * sent. Every line of the transfer must appear exactly once.
     */
    private List<Receipt> receiptsOf(InventoryTransfer transfer, List<LineData> requested) {
        Map<Long, LineData> byLine = new HashMap<>();
        for (LineData input : requested) {
            if (byLine.put(input.lineId(), input) != null) {
                throw new BusinessRuleException("DUPLICATE_DOCUMENT_LINE", "A line appears twice in the receipt");
            }
        }
        Set<Long> lineIds = transfer.getLines().stream().map(InventoryTransferLine::getId)
                .collect(Collectors.toSet());
        for (Long lineId : byLine.keySet()) {
            if (!lineIds.contains(lineId)) {
                throw new BusinessRuleException("TRANSFER_LINE_NOT_FOUND", "The line is not part of the transfer");
            }
        }
        if (byLine.size() != lineIds.size()) {
            throw new BusinessRuleException("TRANSFER_LINES_MISSING", "Every line of the transfer must be received");
        }
        List<Receipt> receipts = new ArrayList<>();
        for (InventoryTransferLine line : transfer.getLines().stream()
                .sorted(Comparator.comparingLong(InventoryTransferLine::getProductId)).toList()) {
            LineData input = byLine.get(line.getId());
            BigDecimal quantity = input.receivedQuantity();
            if (quantity == null || quantity.signum() < 0) {
                throw new BusinessRuleException("INVALID_RECEIVED_QUANTITY", "The received quantity cannot be negative");
            }
            if (quantity.compareTo(line.getQuantity()) > 0) {
                throw new BusinessRuleException("RECEIVED_EXCEEDS_SENT", "More units were received than were sent");
            }
            List<String> serials = SerialNumbers.normalize(input.serialNumbers());
            boolean tracks = products.find(line.getProductId()).map(SaleableProduct::tracksSerials).orElse(false);
            if (tracks) {
                SerialNumbers.requireWhole(quantity);
                SerialNumbers.requireCount(quantity, serials.size());
                Set<String> sent = line.getSerials().stream().map(InventoryTransferSerial::getSerialNumber)
                        .collect(Collectors.toSet());
                for (String serial : serials) {
                    if (!sent.contains(serial)) {
                        throw new BusinessRuleException("SERIAL_NOT_IN_TRANSFER",
                                "The serial number was not sent in the transfer", serial);
                    }
                }
            } else if (!serials.isEmpty()) {
                throw new BusinessRuleException("SERIAL_NOT_EXPECTED", "The product does not track serial numbers");
            }
            receipts.add(new Receipt(line, quantity, serials));
        }
        return receipts;
    }

    /** The units of a product with these serial numbers, keyed by serial number, without locks. */
    private Map<String, ProductSerial> unitsByNumber(long productId, List<String> numbers) {
        Map<String, ProductSerial> rows = new HashMap<>();
        if (!numbers.isEmpty()) {
            serialUnits.findByProductIdAndSerialNumberIn(productId, numbers)
                    .forEach(row -> rows.put(row.getSerialNumber(), row));
        }
        return rows;
    }

    /** The external codes of the units received must not belong to other equipment in this node. */
    private void requireFreeExternalCodes(Receipt receipt) {
        if (receipt.quantity().signum() <= 0 || receipt.serials().isEmpty()) {
            return;
        }
        Map<String, EquipmentUnit> units = new LinkedHashMap<>();
        for (InventoryTransferSerial serial : receipt.line().getSerials()) {
            if (receipt.serials().contains(serial.getSerialNumber())) {
                units.put(serial.getSerialNumber(), new EquipmentUnit(serial.getSerialNumber(),
                        serial.getExternalCode(), serial.getAttributes()));
            }
        }
        equipmentRules.requireFreeCodes(receipt.line().getProductId(), units);
    }

    /** The units received take the external code and the characteristics they were sent with. */
    private void applyReceivedEquipment(Receipt receipt) {
        Map<String, InventoryTransferSerial> sent = new HashMap<>();
        receipt.line().getSerials().forEach(serial -> sent.put(serial.getSerialNumber(), serial));
        for (ProductSerial unit : serialUnits.findForUpdate(receipt.line().getProductId(), receipt.serials())) {
            InventoryTransferSerial data = sent.get(unit.getSerialNumber());
            if (data != null) {
                unit.applyEquipment(data.getExternalCode(), data.getAttributes());
            }
        }
    }

    /** The serial numbers of a line when the product tracks them; none for any other product. */
    private List<String> serialsToTrack(long productId, List<String> serialNumbers) {
        boolean tracks = products.find(productId).map(SaleableProduct::tracksSerials).orElse(false);
        return tracks ? SerialNumbers.normalize(serialNumbers) : List.of();
    }

    /**
     * The branch that sends. A store sends from its own branch only; the central node needs the branch named.
     */
    private long resolveOrigin(Long requested) {
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
            throw new BusinessRuleException("INVENTORY_BRANCH_REQUIRED", "Choose the origin branch of the transfer");
        }
        return requested;
    }

    /** The destination must be another active branch. */
    private void requireRoute(long origin, long destination) {
        if (origin == destination) {
            throw new BusinessRuleException("TRANSFER_SAME_BRANCH", "The destination must be another branch");
        }
        if (!branchDirectory.isActive(destination)) {
            throw new BusinessRuleException("TRANSFER_BRANCH_INACTIVE",
                    "The destination branch does not exist or is inactive");
        }
    }

    /** The lines of a transfer to send: at least one, each product once, each quantity above zero. */
    private static void requireSendLines(List<TransferData.LineData> lines) {
        if (lines.isEmpty()) {
            throw new BusinessRuleException("TRANSFER_WITHOUT_LINES", "A transfer needs at least one line");
        }
        Set<Long> productIds = new HashSet<>();
        for (TransferData.LineData line : lines) {
            if (!productIds.add(line.productId())) {
                throw new BusinessRuleException("DUPLICATE_DOCUMENT_LINE", "A product appears twice in the transfer");
            }
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
            }
        }
    }

    /** The counter of transfer numbers of the origin branch, locked until the transaction ends. */
    private InventoryDocumentCounter lockCounter(long branchId) {
        counters.insertIfAbsent(branchId, COUNTER_TYPE, clock.instant(), by());
        return counters.findForUpdate(branchId, COUNTER_TYPE)
                .orElseThrow(() -> new IllegalStateException("The transfer counter of the branch is missing"));
    }

    private Optional<InventoryTransfer> findSendKey(String idempotencyKey) {
        return idempotencyKey == null ? Optional.empty() : transfers.findBySendIdempotencyKey(idempotencyKey);
    }

    private String by() {
        return auditor.getCurrentAuditor().orElse(SYSTEM_AUDITOR);
    }

    private static List<TransferData.LineData> sortedByProduct(List<TransferData.LineData> lines) {
        return lines.stream().sorted(Comparator.comparingLong(TransferData.LineData::productId)).toList();
    }

    private TransferView view(InventoryTransfer transfer) {
        return TransferView.of(transfer, names());
    }

    private InventoryNames names() {
        return new InventoryNames(branchDirectory, users, products, documents, transfers);
    }

    private static Map<String, Object> snapshot(InventoryTransfer transfer) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("number", transfer.getNumber());
        values.put("originBranchId", transfer.getOriginBranchId());
        values.put("destinationBranchId", transfer.getDestinationBranchId());
        values.put("status", transfer.getStatus().name());
        values.put("lines", transfer.getLines().size());
        return values;
    }

    /** One line of a receipt, checked: the line, the quantity received and the serial numbers received. */
    private record Receipt(InventoryTransferLine line, BigDecimal quantity, List<String> serials) {
    }
}
