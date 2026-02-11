package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.core.datatype.TokenAmount;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 주문 금액과는 별개로 주문 비용에 수수료가 추가될때
 */
@Getter
@RequiredArgsConstructor
public class AddedToCostTradeFee implements TradeFee {
    private final BigDecimal percent;
    private final String percentToken;
    private final List<TokenAmount> flatFees;
}
