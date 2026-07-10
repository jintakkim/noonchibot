package com.hotak.noonchibot.core.pricegap.history;

import java.util.List;

public record PriceGapHistory(
        String tradingPair,
        PriceGapTimeline timeline,
        List<PriceGapHistoryPoint> points
) {
    public PriceGapHistory {
        points = List.copyOf(points);
    }
}
