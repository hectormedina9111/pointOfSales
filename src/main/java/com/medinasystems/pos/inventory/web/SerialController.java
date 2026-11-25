package com.medinasystems.pos.inventory.web;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.inventory.application.SerialQueryService;
import com.medinasystems.pos.inventory.application.SerialTraceView;
import com.medinasystems.pos.inventory.application.SerialView;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.shared.web.PageResponse;

/**
 * Serial numbers of the products that track them. The units of a product are listed per product; the trace follows one
 * serial number through its movements. Each node shows the branches it sees.
 */
@RestController
public class SerialController {

    private final SerialQueryService serials;

    public SerialController(SerialQueryService serials) {
        this.serials = serials;
    }

    /** The units of a product. {@code search} matches part of the serial number, without regard to case. */
    @GetMapping("/api/v1/products/{productId}/serials")
    public PageResponse<SerialView> units(@PathVariable long productId,
                                          @RequestParam(required = false) SerialStatus status,
                                          @RequestParam(required = false) Long branchId,
                                          @RequestParam(required = false) String search,
                                          @PageableDefault(sort = {"serialNumber", "id"}) Pageable pageable) {
        return PageResponse.from(serials.units(productId, status, branchId, search, pageable));
    }

    /**
     * Every unit with this serial number, without regard to case, with its last sale and its movements. Optionally of one
     * product.
     */
    @GetMapping("/api/v1/serials")
    public List<SerialTraceView> trace(@RequestParam String serialNumber,
                                       @RequestParam(required = false) Long productId) {
        return serials.trace(serialNumber, productId);
    }
}
