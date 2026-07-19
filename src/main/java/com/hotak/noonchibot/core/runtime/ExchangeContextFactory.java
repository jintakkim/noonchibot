package com.hotak.noonchibot.core.runtime;

import com.hotak.noonchibot.core.Exchange;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 특정 거래소 전용 Context를 구성한다. refresh와 lifecycle 제어는 ExchangeRuntimeManager가 담당한다.
 */
public interface ExchangeContextFactory {
    Exchange exchange();

    ConfigurableApplicationContext buildContext();
}
