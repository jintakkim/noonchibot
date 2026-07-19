package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExchangeRuntimeManagerTest {
    @Test
    void reusesOneContextAndOneApiAcrossStopAndRestart() {
        ExchangeApi api = mock(ExchangeApi.class);
        CountingLifecycle lifecycle = new CountingLifecycle();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(ExchangeApi.class, () -> api);
        context.registerBean(CountingLifecycle.class, () -> lifecycle);
        AtomicInteger buildCount = new AtomicInteger();
        ExchangeContextFactory factory = new ExchangeContextFactory() {
            @Override
            public Exchange exchange() {
                return Exchange.BINANCE_SPOT;
            }

            @Override
            public ConfigurableApplicationContext buildContext() {
                buildCount.incrementAndGet();
                return context;
            }
        };
        ExchangeRuntimeManager manager = new ExchangeRuntimeManager(List.of(factory));
        ExchangeApiProvider provider = new SimpleExchangeApiProvider(manager);
        manager.afterSingletonsInstantiated();

        ExchangeApi initialApi = provider.getExchange(Exchange.BINANCE_SPOT);
        assertThat(context.isActive()).isTrue();
        assertThat(lifecycle.isRunning()).isTrue();

        manager.start(Exchange.BINANCE_SPOT);
        assertThat(lifecycle.isRunning()).isTrue();

        manager.stop(Exchange.BINANCE_SPOT);
        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(context.isActive()).isTrue();
        assertThat(provider.getExchange(Exchange.BINANCE_SPOT)).isSameAs(initialApi);

        manager.start(Exchange.BINANCE_SPOT);
        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(buildCount).hasValue(1);

        manager.shutdown();
        assertThat(context.isActive()).isFalse();
    }

    private static final class CountingLifecycle implements SmartLifecycle {
        private volatile boolean running;

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return 0;
        }
    }
}
