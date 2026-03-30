package com.hotak.noonchibot.core.config;

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

@Configuration
public class SchedulerConfig {
    @Bean
    @Primary
    public TaskScheduler taskScheduler() {
        return new ThreadPoolTaskSchedulerBuilder()
                .poolSize(5)
                .threadNamePrefix("scheduler-")
                .build();
    }
}
