package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeRateScheduleTest {
    @Test
    @DisplayName("주문 타입과 매수/매도별 수수료율을 조합할 수 있다")
    void rateFor_returnsSideAndOrderTypeSpecificRate() {
        FeeRateSchedule feeRateSchedule = new FeeRateSchedule(
                new BigDecimal("0.001"),
                new BigDecimal("0.015"),
                new BigDecimal("0.0002"),
                new BigDecimal("0.015")
        );

        assertThat(feeRateSchedule.rateFor(OrderType.MARKET, TradeType.BUY)).isEqualByComparingTo("0.001");
        assertThat(feeRateSchedule.rateFor(OrderType.MARKET, TradeType.SELL)).isEqualByComparingTo("0.015");
        assertThat(feeRateSchedule.rateFor(OrderType.LIMIT, TradeType.BUY)).isEqualByComparingTo("0.0002");
        assertThat(feeRateSchedule.rateFor(OrderType.LIMIT, TradeType.SELL)).isEqualByComparingTo("0.015");
    }

    @Test
    @DisplayName("수수료 모델은 base amount * price * rate로 비용을 추정한다")
    void estimateFee_usesQuoteNotionalAndConfiguredRate() {
        RateFeeModel feeModel = new RateFeeModel(FeeRateSchedule.sameRate(new BigDecimal("0.001")));

        BigDecimal fee = feeModel.estimateFee(
                TradeType.SELL,
                OrderType.MARKET,
                new BigDecimal("0.2"),
                new BigDecimal("50000")
        );

        assertThat(fee).isEqualByComparingTo("10");
    }
}
