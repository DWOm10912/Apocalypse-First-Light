package com.antaurora.apofirstlight.worldgen.core;

import java.util.Objects;

/** Side-effect-free failure detail; an empty message is permitted. */
public record GenerationFailure(GenerationFailureReason reason, String message) {
    public GenerationFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
    }
}
