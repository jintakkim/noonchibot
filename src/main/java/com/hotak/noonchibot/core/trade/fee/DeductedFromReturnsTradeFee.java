package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.core.datatype.TokenAmount;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 정산 금액에서 수수료가 제외될 때
 */
@Getter
public class DeductedFromReturnsTradeFee implements TradeFee {
    private final BigDecimal percent;
    private final String percentToken;
    private final List<TokenAmount> flatFees;

    public DeductedFromReturnsTradeFee(BigDecimal percent, String percentToken, List<TokenAmount> flatFees) {
        this.percent = percent;
        this.percentToken = percentToken;
        this.flatFees = flatFees;
    }

    public DeductedFromReturnsTradeFee(BigDecimal percent) {
        this.percent = percent;
        this.percentToken = null;
        this.flatFees = Collections.emptyList();
    }
}
