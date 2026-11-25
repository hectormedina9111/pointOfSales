package com.medinasystems.pos.sync.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.CentralGateway;

/**
 * Central node side: the bean exists so that the application starts, as {@code CentralOutbox} does. Only a store calls
 * the central node, so calling this bean on the central node is a programming error.
 */
@Component
@Profile("central")
public class CentralNodeGateway implements CentralGateway {

    @Override
    public <T> T get(String path, Class<T> type) {
        throw new IllegalStateException("Only a store node calls the central node");
    }

    @Override
    public <T> T post(String path, Object body, Class<T> type) {
        throw new IllegalStateException("Only a store node calls the central node");
    }
}
