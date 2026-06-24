package com.hotak.noonchibot.strategy.bvst.service;

import com.hotak.noonchibot.strategy.ExchangeAdapter;
import com.hotak.noonchibot.strategy.bvst.BvstPool;
import com.hotak.noonchibot.strategy.bvst.BvstPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 하이퍼리쿼드 잔고로 바로 deposit을 체크
 * 잔고 부족만 체크한다.
 * 유저별 입금 검증는 하지 않는다.
 */
@RequiredArgsConstructor
public class BvstHLNonVerifyDepositService implements BvstDepositService {
    private final BigDecimal minDepositPerRequest;
    private final BigDecimal maxDepositPerRequest;
    private final BvstPoolRepository poolRepository;


    @Transactional
    public void deposit(String poolId, long userId, BigDecimal amount) {
        validateAmount(amount);
        BvstPool pool = poolRepository.findById(poolId).orElseThrow(() -> new IllegalStateException("Active pool not found"));
        validatePoolAcceptingDeposits(pool);


    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive: " + amount);
        }
        if (amount.compareTo(minDepositPerRequest) < 0) {
            throw new IllegalArgumentException("Amount below min per request: " + amount + " < " + minDepositPerRequest);
        }
        if (amount.compareTo(maxDepositPerRequest) > 0) {
            throw new IllegalArgumentException("Amount exceeds max per request: " + amount);
        }
    }

    private void validatePoolAcceptingDeposits(BvstPool pool) {
        if (pool.getStatus() != BvstPool.PoolStatus.ACTIVE) {
            throw new IllegalStateException("Pool not accepting deposits, status=" + pool.getStatus());
        }
    }
}
