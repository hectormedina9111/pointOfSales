package com.medinasystems.pos.sales.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.StoreNodeMetrics;

/**
 * Counts the return conflicts of a store for the stores panel. The conflicts are rows of {@code sale_return_conflict}, which
 * exist only on the central node; on a store the count is always zero.
 */
@Component
public class SaleReturnConflicts implements StoreNodeMetrics {

    private final JdbcTemplate jdbc;

    public SaleReturnConflicts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long returnConflicts(long storeNodeId) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM sale_return_conflict WHERE store_node_id = ?",
                Long.class, storeNodeId);
        return count == null ? 0 : count;
    }
}
