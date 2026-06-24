package com.hotak.noonchibot.strategy.arbitrage;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record GapPair(
        String buyExchangeId,
        String sellExchangeId,

        // gap related fields -> rate 단위 제공
        BigDecimal rawGap,
        @JsonProperty("btsmGap")
        BigDecimal buyTakerSellMakerFeeAdjustedGap,
        @JsonProperty("bmsmGap")
        BigDecimal buyMakerSellMakerFeeAdjustedGap,
        @JsonProperty("bmstGap")
        BigDecimal buyMakerSellTakerFeeAdjustedGap,
        @JsonProperty("btstGap")
        BigDecimal buyTakerSellTakerFeeAdjustedGap
) {}
