package com.hotak.noonchibot;

import com.hotak.noonchibot.core.runtime.ExchangeConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = ExchangeConfiguration.class
))
public class NoonchibotApplication {

    public static void main(String[] args) {
        SpringApplication.run(NoonchibotApplication.class, args);
    }

}
