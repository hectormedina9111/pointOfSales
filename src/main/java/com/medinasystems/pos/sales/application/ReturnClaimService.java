package com.medinasystems.pos.sales.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.customer.CustomerDirectory;
import com.medinasystems.pos.customer.CustomerInfo;
import com.medinasystems.pos.organization.BranchDirectory;
import com.medinasystems.pos.organization.BranchInfo;
import com.medinasystems.pos.organization.CompanyDirectory;
import com.medinasystems.pos.organization.CompanyInfo;
import com.medinasystems.pos.sales.domain.ReturnClaimStatus;
import com.medinasystems.pos.sales.domain.ReturnLineRequest;
import com.medinasystems.pos.sales.domain.ReturnSourceLine;
import com.medinasystems.pos.sales.domain.Sale;
import com.medinasystems.pos.sales.domain.SaleLine;
import com.medinasystems.pos.sales.domain.SaleReturnClaim;
import com.medinasystems.pos.sales.domain.SaleType;
import com.medinasystems.pos.sales.infrastructure.PaymentRepository;
import com.medinasystems.pos.sales.infrastructure.SaleLineRepository;
import com.medinasystems.pos.sales.infrastructure.SaleRepository;
import com.medinasystems.pos.sales.infrastructure.SaleReturnClaimRepository;
import com.medinasystems.pos.shared.BusinessRuleException;
import com.medinasystems.pos.shared.ResourceNotFoundException;

/**
 * Central side of the returns of other branches. A store asks for the returnable view of a sale (by id or receipt
 * number), then reserves the quantities of its return: the sale is locked, each quantity is checked against what is left,
 * and a RESERVED claim is written per line. A release gives back the RESERVED claims of a return that failed. The return
 * itself confirms its claims when it is uploaded (see {@code SaleUploadHandler}).
 *
 * <p>What is left is the returnable quantity of each line: the quantity, less the returned quantity, less the claims that
 * are not yet in the documents of this node (see {@link SaleReturnClaimRepository#sumHeldByLine}).
 */
@Service
@Transactional
public class ReturnClaimService {

    private static final Set<ReturnClaimStatus> HOLDING = Set.of(ReturnClaimStatus.RESERVED,
            ReturnClaimStatus.CONFIRMED);

    private final SaleRepository sales;
    private final SaleLineRepository lines;
    private final PaymentRepository payments;
    private final SaleReturnClaimRepository claims;
    private final CustomerDirectory customers;
    private final BranchDirectory branches;
    private final CompanyDirectory companies;
    private final Clock clock;

    public ReturnClaimService(SaleRepository sales, SaleLineRepository lines, PaymentRepository payments,
                              SaleReturnClaimRepository claims, CustomerDirectory customers, BranchDirectory branches,
                              CompanyDirectory companies, Clock clock) {
        this.sales = sales;
        this.lines = lines;
        this.payments = payments;
        this.claims = claims;
        this.customers = customers;
        this.branches = branches;
        this.companies = companies;
        this.clock = clock;
    }

    /**
     * The returnable view of a paid or partly returned sale, by id.
     *
     * @throws ResourceNotFoundException when the sale does not exist or cannot be returned
     */
    @Transactional(readOnly = true)
    public ReturnableView returnable(long saleId) {
        return view(findReturnable(sales.findById(saleId), "sale", saleId));
    }

    /** The same view, found by the receipt number of a sale (returns have their own receipt numbers). */
    @Transactional(readOnly = true)
    public ReturnableView returnableByReceipt(String receiptNumber) {
        return view(findReturnable(sales.findByReceiptNumberAndType(receiptNumber, SaleType.SALE), "sale",
                receiptNumber));
    }

    /**
     * Holds the quantities of a return. Asking again with the same claim key answers the current view without changing
     * anything. Otherwise each line must fit in what is left, or the whole request is refused.
     *
     * @throws BusinessRuleException RETURN_NOT_ALLOWED, RETURN_LINES_REQUIRED, DUPLICATE_RETURN_LINE,
     *                               RETURN_LINE_NOT_FOUND, INVALID_QUANTITY or RETURN_QUANTITY_EXCEEDED
     */
    public ReturnableView reserve(long saleId, String claimKey, List<ReturnLineRequest> requested, long storeNodeId) {
        Sale sale = sales.findByIdForUpdate(saleId).orElseThrow(() -> new ResourceNotFoundException("sale", saleId));
        if (!sale.isReturnable()) {
            throw new BusinessRuleException("RETURN_NOT_ALLOWED", "Only a paid sale can be returned");
        }
        Map<Long, SaleReturnClaim> existing = claims.findByClaimKeyAndSaleId(claimKey, saleId).stream()
                .collect(Collectors.toMap(SaleReturnClaim::getSaleLineId, Function.identity()));
        if (existing.values().stream().anyMatch(SaleReturnClaim::isHolding)) {
            return view(sale);
        }
        if (requested == null || requested.isEmpty()) {
            throw new BusinessRuleException("RETURN_LINES_REQUIRED", "The return needs at least one line");
        }
        checkQuantities(sale, requested);
        for (ReturnLineRequest request : requested) {
            BigDecimal quantity = request.quantity().setScale(3, RoundingMode.HALF_UP);
            SaleReturnClaim claim = existing.get(request.saleLineId());
            if (claim == null) {
                claims.save(new SaleReturnClaim(claimKey, saleId, request.saleLineId(), quantity, storeNodeId));
            } else {
                claim.reserve(quantity, storeNodeId);
            }
        }
        return view(sale);
    }

    /**
     * Gives back the RESERVED claims of a return made by this store. A key with no reserved claims is a no-op.
     */
    public ReleaseClaimsResult release(String claimKey, long storeNodeId) {
        int released = 0;
        for (SaleReturnClaim claim : claims.findByClaimKeyAndStoreNodeId(claimKey, storeNodeId)) {
            if (claim.isReserved()) {
                claim.release();
                released++;
            }
        }
        return new ReleaseClaimsResult(claimKey, released);
    }

    private void checkQuantities(Sale sale, List<ReturnLineRequest> requested) {
        Map<Long, BigDecimal> held = held(sale.getId());
        Map<Long, SaleLine> byId = sale.getLines().stream()
                .collect(Collectors.toMap(SaleLine::getId, Function.identity()));
        Set<Long> seen = new HashSet<>();
        for (ReturnLineRequest request : requested) {
            if (!seen.add(request.saleLineId())) {
                throw new BusinessRuleException("DUPLICATE_RETURN_LINE", "A line is returned twice");
            }
            SaleLine line = byId.get(request.saleLineId());
            if (line == null) {
                throw new BusinessRuleException("RETURN_LINE_NOT_FOUND", "The line is not part of the sale");
            }
            if (request.quantity() == null || request.quantity().signum() <= 0) {
                throw new BusinessRuleException("INVALID_QUANTITY", "The quantity must be greater than zero");
            }
            BigDecimal returnable = ReturnSourceLine.of(line, held.getOrDefault(line.getId(), BigDecimal.ZERO))
                    .returnableQuantity();
            if (request.quantity().setScale(3, RoundingMode.HALF_UP).compareTo(returnable) > 0) {
                throw new BusinessRuleException("RETURN_QUANTITY_EXCEEDED",
                        "The quantity is more than the quantity left to return");
            }
        }
    }

    private static Sale findReturnable(Optional<Sale> found, String resource, Object id) {
        return found.filter(Sale::isReturnable).orElseThrow(() -> new ResourceNotFoundException(resource, id));
    }

    private ReturnableView view(Sale sale) {
        long saleId = sale.getId();
        return ReturnableView.of(sale, sameDay(sale), customerName(sale.getCustomerId()),
                branchName(sale.getBranchId()), false, ReturnRefunds.byLine(lines, saleId),
                ReturnRefunds.byMethod(payments, saleId), held(saleId));
    }

    private Map<Long, BigDecimal> held(long saleId) {
        Map<Long, BigDecimal> held = new HashMap<>();
        for (Object[] row : claims.sumHeldByLine(saleId, HOLDING, SaleType.RETURN)) {
            held.put((Long) row[0], (BigDecimal) row[1]);
        }
        return held;
    }

    /** True when the sale was paid on the same calendar day as now, in the company's time zone. False without a company. */
    private boolean sameDay(Sale sale) {
        return companies.find().map(CompanyInfo::timeZone).map(ZoneId::of)
                .map(zone -> sale.getPaidAt().atZone(zone).toLocalDate()
                        .equals(clock.instant().atZone(zone).toLocalDate()))
                .orElse(false);
    }

    private String customerName(Long customerId) {
        long id = customerId == null ? CustomerDirectory.FINAL_CONSUMER_ID : customerId;
        return customers.find(id).map(CustomerInfo::displayName).orElse(null);
    }

    private String branchName(Long branchId) {
        return branches.info(branchId).map(BranchInfo::name).orElse(null);
    }
}
