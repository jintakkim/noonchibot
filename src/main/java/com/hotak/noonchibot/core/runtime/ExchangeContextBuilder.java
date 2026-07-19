package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Root의 공용 인프라는 상속하고 거래소 전용 빈은 child Context에 격리한다. */
@Component
public final class ExchangeContextBuilder {
    private final ConfigurableApplicationContext rootContext;

    public ExchangeContextBuilder(ConfigurableApplicationContext rootContext) {
        this.rootContext = rootContext;
    }

    public ConfigurableApplicationContext build(Exchange exchange, Class<?>... configurationClasses) {
        Objects.requireNonNull(exchange, "exchange");
        Objects.requireNonNull(configurationClasses, "configurationClasses");
        if (configurationClasses.length == 0) {
            throw new IllegalArgumentException("At least one exchange configuration class is required");
        }

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setId("exchange-" + exchange.getId());
        context.setParent(rootContext);
        context.register(configurationClasses);
        return context;
    }
}
