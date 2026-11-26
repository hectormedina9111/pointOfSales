package com.medinasystems.pos.inventory.application;

import java.util.Map;

/**
 * One unit of equipment as it is loaded or edited: the serial number, the external code (optional) and the characteristics,
 * by code. The rules return it normalized.
 */
public record EquipmentUnit(String serialNumber, String externalCode, Map<String, Object> attributes) {
}
