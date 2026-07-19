package com.hotak.noonchibot.core.runtime;

import org.springframework.context.annotation.Configuration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 거래소 child context의 구성 진입점.
 * Root component scan에서는 제외되고 ExchangeContextFactory가 명시적으로 등록한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Configuration(proxyBeanMethods = false)
public @interface ExchangeConfiguration {
}
