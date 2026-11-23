package com.medinasystems.pos.shared;

import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Identity of this installation, set by the {@code central} or {@code store}
 * profile.
 *
 * @param role        whether this node is the central node or a store
 * @param number      0 for the central node, 1 or higher for stores
 * @param centralUrl  base URL of the central node; required for stores
 */
@ConfigurationProperties(prefix = "pos.node")
public record NodeProperties(NodeRole role, int number, String centralUrl) {

    public NodeProperties {
        Objects.requireNonNull(role, "pos.node.role is required (central or store)");
        if (role == NodeRole.CENTRAL && number != 0) {
            throw new IllegalArgumentException("The central node must use pos.node.number=0, got " + number);
        }
        if (role == NodeRole.STORE && number < 1) {
            throw new IllegalArgumentException("A store node must use pos.node.number >= 1, got " + number);
        }
        if (role == NodeRole.STORE && (centralUrl == null || centralUrl.isBlank())) {
            throw new IllegalArgumentException("A store node requires pos.node.central-url");
        }
    }

    public boolean isCentral() {
        return role == NodeRole.CENTRAL;
    }

    public boolean isStore() {
        return role == NodeRole.STORE;
    }
}
