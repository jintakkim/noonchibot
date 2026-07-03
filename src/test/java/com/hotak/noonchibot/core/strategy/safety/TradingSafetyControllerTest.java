package com.hotak.noonchibot.core.strategy.safety;

import com.hotak.noonchibot.core.event.TestEventSubscriber;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
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

    @Test
    void connect_pausesTradingWhenOrderFailureIsReceived() {
        TestEventSubscriber subscriber = new TestEventSubscriber();
        TradingSafetyController controller = new TradingSafetyController();

        controller.connect(subscriber);
        subscriber.getSubscriptionsFor(OrderEvent.Failed.class)
                .getFirst()
                .handler()
                .onEvent(new OrderEvent.Failed(
                        "BTC-USDT",
                        "cid-1",
                        null,
                        new RuntimeException("exchange unavailable")
                ));

        assertThat(subscriber.isSubscribed(OrderEvent.Failed.class)).isTrue();
        assertThat(controller.status()).isEqualTo(TradingStatus.TRADING_PAUSED);
    }
}
