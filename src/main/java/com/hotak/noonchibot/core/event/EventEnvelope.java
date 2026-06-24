package com.hotak.noonchibot.core.event;

public record EventEnvelope<E extends Event>(
        E payload,
        EventMetadata metadata
) {}
