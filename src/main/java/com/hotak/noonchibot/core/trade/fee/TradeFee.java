package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.core.datatype.TokenAmount;
import com.hotak.noonchibot.core.datatype.TradeType;

import java.math.BigDecimal;
import java.util.List;

public interface TradeFee {
    /**
     * 거래 금액의 몇 %를 떼어가는지 나타내는 수수료율
     */
    BigDecimal getPercent();

    /**
     * 수수료를 낼 때 특정 코인으로 강제되거나 지정된 경우
     * binance의 경우 BNB로 강제할 수 있음
     */
    String getPercentToken();

    /**
     * 거래 금액과 상관없이 무조건 내야 하는 고정 비용들
     */
    List<TokenAmount> getFlatFees();

    static TradeFee newSpotFee(TradeFeeSchema schema, TradeType tradeType, BigDecimal percent, String percentToken, List<TokenAmount> flatFees) {
        boolean isAddedToCost = (tradeType == TradeType.BUY && (!schema.buyPercentFeeDeductedFromReturns() || schema.percentFeeToken() != null));
        if (isAddedToCost) {
            return new AddedToCostTradeFee(percent, percentToken, flatFees);
        } else {
            return new DeductedFromReturnsTradeFee(percent, percentToken, flatFees);
        }
    }
}
