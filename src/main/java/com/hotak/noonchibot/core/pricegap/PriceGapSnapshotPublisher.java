package com.hotak.noonchibot.core.pricegap;

import java.util.List;

@FunctionalInterface
public interface PriceGapSnapshotPublisher {
    PriceGapSnapshotPublisher NOOP = snapshots -> {};

    void publish(List<PriceGapSnapshot> snapshots);
}
