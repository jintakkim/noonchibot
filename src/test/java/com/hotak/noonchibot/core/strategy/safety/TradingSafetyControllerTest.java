package com.hotak.noonchibot.core.strategy.safety;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TradingSafetyControllerTest {
    @Test
    void pauseAndManualResume_changeTradingStatus() {
        TradingSafetyController controller = new TradingSafetyController();

        controller.pause(new RuntimeException("order failed"));
        assertThat(controller.status()).isEqualTo(TradingStatus.TRADING_PAUSED);

        controller.resumeAfterReconciliation();
        assertThat(controller.status()).isEqualTo(TradingStatus.RUNNING);
    }
}
