package com.hotak.noonchibot.core.trade.fee;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * trade fee 객체를 만들기 위해 필요한 정보 전달
 */
public record TradeFeeSchema(
        /*
         * 수수료가 특정 토큰으로 강제되는 경우 해당 토큰의 심볼 (예: "BNB").
         * 지정되지 않은 경우(null) 일반적인 수수료 규칙을 따른다.
         */
        String percentFeeToken,
        /*
         * Maker(지정가) 주문 시 적용되는 수수료율
         */
        BigDecimal makerPercentFee,
        /*
         * Taker(시장가) 주문 시 적용되는 수수료율
         */
        BigDecimal takerPercentFee,
        /*
         * 매수(Buy) 시 수수료를 나중에 수익에서 차감할지 여부.
         */
        boolean buyPercentFeeDeductedFromReturns,
        /*
         * Maker 주문 시 발생하는 고정 수수료 목록 (가스비 등)
         */
        List<TokenAmount> makerFixedFees,
        /*
         * Taker 주문 시 발생하는 고정 수수료 목록 (가스비 등)
         */
        List<TokenAmount> takerFixedFees
) {
    public TradeFeeSchema(
            String percentFeeToken,
            BigDecimal makerPercentFee,
            BigDecimal takerPercentFee,
            boolean buyPercentFeeDeductedFromReturns,
            List<TokenAmount> makerFixedFees,
            List<TokenAmount> takerFixedFees
    ) {
        this.percentFeeToken = percentFeeToken;
        this.makerPercentFee = makerPercentFee != null ? makerPercentFee : BigDecimal.ZERO;
        this.takerPercentFee = takerPercentFee != null ? takerPercentFee : BigDecimal.ZERO;
        this.buyPercentFeeDeductedFromReturns = buyPercentFeeDeductedFromReturns;
        this.makerFixedFees = makerFixedFees != null ? new ArrayList<>(makerFixedFees) : new ArrayList<>();
        this.takerFixedFees = takerFixedFees != null ? new ArrayList<>(takerFixedFees) : new ArrayList<>();
        validateSchema();
    }

    private void validateSchema() {
        // 특정 토큰으로 수수료를 낸다면(예: BNB), 수익에서 차감하는 방식(후불)은 불가능하다.
        if (this.percentFeeToken != null && this.buyPercentFeeDeductedFromReturns) {
            throw new IllegalArgumentException("percentFeeToken이 설정된 경우 buyPercentFeeDeductedFromReturns는 true일 수 없습니다.");
        }
    }

//    static TradeFee newSpotFee(TradeFeeSchema schema, TradeType tradeType, boolean isMaker) {
//        BigDecimal percent = isMaker ? schema.makerPercentFee() : schema.takerPercentFee();
//        List<TokenAmount> flatFees = isMaker ? schema.makerFixedFees() : schema.takerFixedFees();
//        boolean isAddedToCost = (tradeType == TradeType.BUY
//                && (!schema.buyPercentFeeDeductedFromReturns() || schema.percentFeeToken() != null));
//
//        return isAddedToCost
//                ? new AddedToCostTradeFee(percent, schema.percentFeeToken(), flatFees)
//                : new DeductedFromReturnsTradeFee(percent, schema.percentFeeToken(), flatFees);
//    }
//
//    static TradeFee newPerpetualFee(TradeFeeSchema schema, PositionAction positionAction, boolean isMaker) {
//        BigDecimal percent = isMaker ? schema.makerPercentFee() : schema.takerPercentFee();
//        List<TokenAmount> flatFees = isMaker ? schema.makerFixedFees() : schema.takerFixedFees();
//
//        boolean isAddedToCost = (positionAction == PositionAction.OPEN || schema.percentFeeToken() != null);
//
//        return isAddedToCost
//                ? new AddedToCostTradeFee(percent, schema.percentFeeToken(), flatFees)
//                : new DeductedFromReturnsTradeFee(percent, schema.percentFeeToken(), flatFees);
//    }
}