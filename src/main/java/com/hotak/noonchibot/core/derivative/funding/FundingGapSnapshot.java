package com.hotak.noonchibot.core.derivative.funding;

import java.util.List;
import java.util.Map;

public record FundingGapSnapshot(
        Map<String, FundingGapHistory> histories,
        List<FundingGapSummary> summaries
) {
    public static final FundingGapSnapshot EMPTY = new FundingGapSnapshot(Map.of(), List.of());

    public FundingGapSnapshot {
        histories = Map.copyOf(histories);
        summaries = List.copyOf(summaries);
    }
}
