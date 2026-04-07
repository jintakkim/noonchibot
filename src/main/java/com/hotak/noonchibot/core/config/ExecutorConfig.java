package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.MainExecutorImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {
    @Bean
    public MainExecutor mainExecutor() {
        return new MainExecutorImpl();
    }

    @Bean
    public IoExecutor ioExecutor() {
        return new VirtualThreadIoExecutor();
    }
}
