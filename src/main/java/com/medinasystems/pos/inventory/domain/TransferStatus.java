package com.medinasystems.pos.inventory.domain;

/**
 * State of an inventory transfer. A transfer is IN_TRANSIT from the moment the origin branch sends it until the destination
 * receives it: RECEIVED when every unit arrived, RECEIVED_WITH_DIFFERENCES when some quantity or serial did not.
 */
public enum TransferStatus {
    IN_TRANSIT,
    RECEIVED,
    RECEIVED_WITH_DIFFERENCES
}
