package com.hotak.noonchibot.core.utils;

import com.hotak.noonchibot.client.config.TradeFeeSchemaLoader;
import com.hotak.noonchibot.core.datatype.OrderType;
import com.hotak.noonchibot.core.datatype.TokenAmount;
import com.hotak.noonchibot.core.datatype.TradeFeeSchema;
import com.hotak.noonchibot.core.datatype.TradeType;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class TradeFeeBuilder {
    private final TradeFeeSchemaLoader tradeFeeSchemaLoader;

    /**
     * WARNING: Do not use this method when creating order.
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
        BigDecimal feePercent = isMaker ? tradeFeeSchema.makerPercentFee() : tradeFeeSchema.takerPercentFee();
        List<TokenAmount> fixedFees = new ArrayList<>(isMaker ? tradeFeeSchema.makerFixedFees() : tradeFeeSchema.takerFixedFees());

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
     * WARNING: Do not use this method when creating order.
     */
    public TradeFee buildTradeFee(String exchange, boolean isMaker, TradeType tradeType) {
        return buildTradeFee(exchange, isMaker, null, null, OrderType.LIMIT, tradeType, null, null, null);
    }
}