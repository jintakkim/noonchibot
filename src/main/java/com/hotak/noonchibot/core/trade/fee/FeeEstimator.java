package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.core.datatype.*;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class FeeEstimator {
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;

    /**
     * WARNING: 오더를 만들때 해당 메서드를 사용해서는 안된다.
     */
    public TradeFee buildTradeFee(
            String exchange,
            boolean isMaker,
            String baseCurrency,
            String quoteCurrency,
            OrderType orderType,
            TradeType tradeType,
            BigDecimal amount,
            BigDecimal price,
            List<TokenAmount> extraFlatFees
    ) {
        TradeFeeSchema tradeFeeSchema = tradeFeeSchemaLoader.get(exchange);
        // Maker/Taker 여부에 따른 수수료율 결정
        BigDecimal feePercent = isMaker ? tradeFeeSchema.makerPercentFee() : tradeFeeSchema.takerPercentFee();
        List<TokenAmount> fixedFees = new ArrayList<>(isMaker ? tradeFeeSchema.makerFixedFees() : tradeFeeSchema.takerFixedFees());
        // 추가 고정 수수료 병합
        if (extraFlatFees != null && !extraFlatFees.isEmpty()) {
            fixedFees.addAll(extraFlatFees);
        }
        return TradeFee.newSpotFee(
                tradeFeeSchema,
                tradeType,
                feePercent,
                tradeFeeSchema.percentFeeToken(),
                fixedFees
        );
    }

    /**
     * WARNING: 오더를 만들때 해당 메서드를 사용해서는 안된다.
     */
    public TradeFee buildTradeFee(String exchange, boolean isMaker, TradeType tradeType) {
        return buildTradeFee(exchange, isMaker, null, null, OrderType.LIMIT, tradeType, null, null, null);
    }
}