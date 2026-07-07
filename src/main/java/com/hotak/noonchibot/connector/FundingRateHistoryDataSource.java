package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.derivative.funding.FundingRatePoint;

import java.time.Instant;
import java.util.List;

public interface FundingRateHistoryDataSource {
    List<FundingRatePoint> fetch(String tradingPair, Instant from, Instant to);
}
