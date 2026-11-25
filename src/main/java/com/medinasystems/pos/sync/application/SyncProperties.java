package com.medinasystems.pos.sync.application;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sync settings bound from {@code pos.sync.*}.
 *
 * @param activationCode     store side: the code the store uses to activate itself (env POS_ACTIVATION_CODE)
 * @param devActivationCodes central side, LOCAL ONLY: "nodeNumber:code" pairs, comma separated, such as
 *                           "1:STORE1-DEV-CODE,2:STORE2-DEV-CODE" (env POS_DEV_ACTIVATION_CODES)
 * @param extraSequences     store side: named sequences that are not the identity of a table and must also live in
 *                           the node's id range, such as sale_internal_number_seq (set in application.yml)
 */
@ConfigurationProperties("pos.sync")
public record SyncProperties(String activationCode, String devActivationCodes, List<String> extraSequences) {
}
