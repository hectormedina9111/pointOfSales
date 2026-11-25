package com.medinasystems.pos.sync.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.medinasystems.pos.sync.infrastructure.IdRangeSequenceAdjuster;

/**
 * Central side, at every startup: caps the id sequences of the central node at the central range. A store creates its
 * own ids inside its range, so a central sequence must never reach a store range. A central database whose sequence is
 * already past the range does not start.
 */
@Component
@Profile("central")
public class CentralSequenceCap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CentralSequenceCap.class);

    private final IdRangeSequenceAdjuster sequences;

    public CentralSequenceCap(IdRangeSequenceAdjuster sequences) {
        this.sequences = sequences;
    }

    @Override
    public void run(ApplicationArguments args) {
        sequences.capCentral();
        log.info("Central id sequences capped at {}", IdRangeSequenceAdjuster.CENTRAL_RANGE_END);
    }
}
