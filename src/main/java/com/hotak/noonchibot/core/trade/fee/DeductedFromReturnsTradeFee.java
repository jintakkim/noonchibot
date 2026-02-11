package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.core.datatype.TokenAmount;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 정산 금액에서 수수료가 제외될 때
 */
@Getter
@RequiredArgsConstructor
public class DeductedFromReturnsTradeFee implements TradeFee {
    private final BigDecimal percent;
    private final String percentToken;
    private final List<TokenAmount> flatFees;

}
