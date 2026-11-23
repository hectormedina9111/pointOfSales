package com.medinasystems.pos.shared;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class NodePropertiesTest {

    @Test
    void acceptsCentralWithNumberZero() {
        assertThatNoException().isThrownBy(() -> new NodeProperties(NodeRole.CENTRAL, 0, null));
    }

    @Test
    void rejectsCentralWithNonZeroNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NodeProperties(NodeRole.CENTRAL, 1, null));
    }

    @Test
    void rejectsStoreWithNumberZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NodeProperties(NodeRole.STORE, 0, "http://central:8080"));
    }

    @Test
    void acceptsStoreWithNumberAndCentralUrl() {
        assertThatNoException()
                .isThrownBy(() -> new NodeProperties(NodeRole.STORE, 1, "http://central:8080"));
    }

    @Test
    void rejectsStoreWithoutCentralUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NodeProperties(NodeRole.STORE, 1, null));
    }

    @Test
    void rejectsNullRole() {
        assertThatNullPointerException()
                .isThrownBy(() -> new NodeProperties(null, 0, null));
    }
}
