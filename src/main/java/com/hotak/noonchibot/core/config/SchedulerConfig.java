package com.hotak.noonchibot.core.config;

import com.hotak.noonchibot.core.RealtimeClock;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

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

    @Bean
    public RealtimeClock realtimeClock(TaskScheduler taskScheduler) {
        return new RealtimeClock(taskScheduler, Duration.ofMillis(100));
    }
}
