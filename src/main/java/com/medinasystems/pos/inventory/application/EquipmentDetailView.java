package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.medinasystems.pos.catalog.AttributeDefinition;
import com.medinasystems.pos.inventory.domain.SerialStatus;
import com.medinasystems.pos.inventory.application.SerialTraceView.MovementView;

/**
 * One unit of equipment with its movements, and the characteristic template of its category so that the screen can label the
 * values.
 */
public record EquipmentDetailView(long id, long productId, String productSku, String productName, Long categoryId,
                                  String serialNumber, String externalCode, Map<String, Object> attributes,
                                  SerialStatus status, long branchId, String branchName, Instant receivedAt,
                                  Instant soldAt, List<MovementView> movements,
                                  List<AttributeDefinition> attributeDefinitions) {
}
