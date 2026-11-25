package com.medinasystems.pos.sales.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * The serial number of one unit of a sale line. The sale keeps the number only: the inventory module keeps its own rows,
 * because modules do not share tables. {@code returned} is true once the unit came back on a return of this line.
 */
@Entity
@Table(name = "sale_line_serial")
public class SaleLineSerial extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "sale_line_id", nullable = false)
    private SaleLine saleLine;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(nullable = false)
    private boolean returned;

    protected SaleLineSerial() {
    }

    SaleLineSerial(SaleLine saleLine, String serialNumber, boolean returned) {
        this.saleLine = saleLine;
        this.serialNumber = serialNumber;
        this.returned = returned;
    }

    void markReturned() {
        this.returned = true;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public boolean isReturned() {
        return returned;
    }
}
