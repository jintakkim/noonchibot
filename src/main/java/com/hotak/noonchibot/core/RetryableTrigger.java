package com.hotak.noonchibot.core;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;

import java.time.Duration;
import java.time.Instant;

/**
 * 작업 성공시 -> 마지막 완료 시점 기준 normalInterval 만큼 대기
 * 작업 실패시 -> 마지막 완료 시점 기준 retryInterval 만큼 대기
 */
@RequiredArgsConstructor
public class RetryableTrigger implements Trigger {

    private final Duration normalInterval;
    private final Duration retryInterval;
    private boolean lastSucceeded = true;


    @Override
    public Instant nextExecution(TriggerContext context) {
        if (lastSucceeded) {
            return Instant.now().plus(normalInterval);
        } else {
            return Instant.now().plus(retryInterval);
        }
    }

    public void recordSuccess() {
        lastSucceeded = true;
    }

    public void recordFailure() {
        lastSucceeded = false;
    }
}
