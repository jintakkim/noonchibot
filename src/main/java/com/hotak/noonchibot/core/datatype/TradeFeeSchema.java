package com.hotak.noonchibot.core.datatype;

import java.math.BigDecimal;
import java.util.List;

public record TradeFeeSchema(
        String percentFeeToken,
        BigDecimal makerPercentFee,
        BigDecimal takerPercentFee,
        boolean buyPercentFeeDeductedFromReturns,
        List<TokenAmount> makerFixedFees,
        List<TokenAmount> takerFixedFees
) {
    public TradeFeeSchema {
        makerPercentFee = (makerPercentFee == null) ? BigDecimal.ZERO : makerPercentFee;
        takerPercentFee = (takerPercentFee == null) ? BigDecimal.ZERO : takerPercentFee;
        makerFixedFees = (makerFixedFees == null) ? List.of() : List.copyOf(makerFixedFees);
        takerFixedFees = (takerFixedFees == null) ? List.of() : List.copyOf(takerFixedFees);
        validateSchema(percentFeeToken, buyPercentFeeDeductedFromReturns);
    }

    private static void validateSchema(String percentFeeToken, boolean buyPercentFeeDeductedFromReturns) {
        if (percentFeeToken != null && buyPercentFeeDeductedFromReturns) {
            throw new IllegalArgumentException(
                    "buyPercentFeeDeductedFromReturns cannot be true when percentFeeToken is specified."
            );
        }
    }
}