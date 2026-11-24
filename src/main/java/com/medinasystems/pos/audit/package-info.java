/**
 * Audit log: stores every {@code AuditEvent} published by other modules, in the same transaction
 * as the change it describes, and exposes the log for queries.
 */
@ApplicationModule(displayName = "Audit")
package com.medinasystems.pos.audit;

import org.springframework.modulith.ApplicationModule;
