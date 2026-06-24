package com.hotak.noonchibot.core.event;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(
        String corrId, Instant timestamp
) {
    public static EventMetadata newRoot() {
        return new EventMetadata(UUID.randomUUID().toString(), Instant.now());
    }
    public EventMetadata derive() {
        return new EventMetadata(corrId, Instant.now());
    }
}
