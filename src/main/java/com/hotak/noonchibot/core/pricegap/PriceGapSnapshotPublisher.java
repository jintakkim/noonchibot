package com.hotak.noonchibot.core.pricegap;

@FunctionalInterface
public interface PriceGapSnapshotPublisher {
    PriceGapSnapshotPublisher NOOP = snapshot -> {};

    void publish(PriceGapSnapshot snapshot);
}
