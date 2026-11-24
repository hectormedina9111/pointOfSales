package com.medinasystems.pos.sync.application;

import java.time.Instant;

/** A new activation code. It is shown once: the central node keeps only its hash. */
public record ActivationCodeView(String code, Instant expiresAt) {
}
