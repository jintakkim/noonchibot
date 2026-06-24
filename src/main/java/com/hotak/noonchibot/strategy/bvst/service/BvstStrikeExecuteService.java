package com.hotak.noonchibot.strategy.bvst.service;

import com.hotak.noonchibot.strategy.bvst.BvstPool;
import com.hotak.noonchibot.strategy.bvst.BvstPoolRepository;
import com.hotak.noonchibot.strategy.bvst.BvstStrike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RequiredArgsConstructor
@Slf4j
public class BvstStrikeExecuteService {
    private final BvstPoolRepository poolRepository;
    private final BvstStrikeEventRepository strikeEventRepository;
    private final BvstPendingDepositRepository pendingDepositRepository;
    private final BvstPendingWithdrawRepository pendingWithdrawRepository;
    private final BvstNavCalculator navCalculator;
    private final BvstStrikeContext strikeContext;
    private final BvstStrikeOrderIdGenerator idGenerator;
    private final BvstBasketAllocator allocator;
    private final BvstOrderManager orderManager;
    private final PriceProvider priceProvider;


    public void executeStrike() {


        BvstStrikeEvent strike = BvstStrike.create(strikeId, scheduledAt);

        try {
            // Phase 1: NAV 측정
            measureNavBefore(strike);

            // Phase 2: 매수/매도 실행
            executeOrders(strike);

            // Phase 3: 발행/소각
            settle(strike);

            strike.setStatus(StrikeStatus.COMPLETED);
            strike.setSettledAt(Instant.now());
        } catch (Exception e) {
            log.error("Strike {} failed", strikeId, e);
            handleFailure(strike, e);
        } finally {
            strikeEventRepository.save(strike);
        }
    }

    private void measureNavBefore(BvstStrikeEvent strike) {
        BvstPool pool = poolRepository.findById(strike.getPoolId()).orElseThrow();
        BigDecimal nav = navCalculator.calculate(pool.getPoolId());
        strike.recordNavBefore(nav, pool.getTotalShares());
        strike.setStatus(StrikeStatus.EXECUTING);
        strike.setExecutionStartedAt(Instant.now());
    }

    private void executeOrders(BvstStrikeEvent strike) {
        // 1. pending 자금 합산
        List<BvstPendingDeposit> deposits = pendingDepositRepository.findPendingForStrike(strike.getStrikeId());
        List<BvstPendingWithdraw> withdraws = pendingWithdrawRepository.findPendingForStrike(strike.getStrikeId());

        BigDecimal totalDepositCash = sumDeposits(deposits);
        BigDecimal totalWithdrawShares = sumWithdrawShares(withdraws);
        BigDecimal estimatedWithdrawCash = totalWithdrawShares.multiply(strike.getSharePriceAtStrike());

        // netting
        BigDecimal netCash = totalDepositCash.subtract(estimatedWithdrawCash);

        strike.setTotalDepositCash(totalDepositCash);
        strike.setTotalWithdrawShares(totalWithdrawShares);
        strike.setNetCashFlow(netCash);

        // 2. reference price 캡처
        Map<String, BigDecimal> refPrices = priceProvider.snapshotPrices(allocator.getAllCoins());
        strikeContext.start(strike.getStrikeId(), refPrices);

        // 3. 실제 매매 실행
        if (netCash.signum() > 0) {
            // Net inflow — 매수
            executeBuyOrders(strike.getStrikeId(), netCash);
        } else if (netCash.signum() < 0) {
            // Net outflow — 매도
            executeSellOrders(strike.getStrikeId(), netCash.abs());
        }

        // 4. 모든 fill 완료 대기
        waitForAllFills(strike.getStrikeId());

        strike.setExecutionCompletedAt(Instant.now());
        strike.setStatus(StrikeStatus.SETTLING);

        // 5. 누적 결과 회수
        StrikeExecutionSummary summary = strikeContext.finalize(strike.getStrikeId());
        strike.setTotalCashSpent(summary.totalCashSpent());
        strike.setTotalCashReceived(summary.totalCashReceived());
        strike.setTotalSlippage(summary.totalSlippage());
        strike.setTotalFees(summary.totalFees());
    }

    private void executeBuyOrders(String strikeId, BigDecimal totalCash) {
        Map<String, BigDecimal> longAllocations = allocator.allocateLong(totalCash.divide(BigDecimal.valueOf(2), ...));
        Map<String, BigDecimal> shortAllocations = allocator.allocateShort(totalCash.divide(BigDecimal.valueOf(2), ...));

        // TWAP으로 분할 실행
        Duration window = Duration.ofMinutes(30);
        int slices = 30;

        for (int slice = 0; slice < slices; slice++) {
            int seq = slice;

            longAllocations.forEach((coin, totalUsd) -> {
                BigDecimal sliceUsd = totalUsd.divide(BigDecimal.valueOf(slices), ...);
                String cloid = idGenerator.generate(strikeId, coin, TradeType.BUY, seq);
                orderManager.submitLimitBuy(coin, sliceUsd, cloid);
            });

            shortAllocations.forEach((coin, totalUsd) -> {
                BigDecimal sliceUsd = totalUsd.divide(BigDecimal.valueOf(slices), ...);
                String cloid = idGenerator.generate(strikeId, coin, TradeType.SELL, seq);
                orderManager.submitLimitSell(coin, sliceUsd, cloid);
            });

            try {
                Thread.sleep(window.dividedBy(slices).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private void waitForAllFills(String strikeId) {
        // strike의 모든 주문이 filled/canceled 될 때까지 대기
        // OrderTracker 상태 모니터링
        // timeout 설정 필요 (예: 2시간)
    }

    private void settle(BvstStrikeEvent strike) {
        BigDecimal totalDepositCash = strike.getTotalDepositCash();
        BigDecimal totalSlippage = strike.getTotalSlippage();

        // slippage rate (deposit 자금 대비 비율)
        BigDecimal slippageRate = totalDepositCash.signum() == 0
                ? BigDecimal.ZERO
                : totalSlippage.divide(totalDepositCash, 8, RoundingMode.HALF_UP);

        // Pending Deposit 처리
        List<BvstPendingDeposit> deposits = pendingDepositRepository.findPendingForStrike(strike.getStrikeId());
        BigDecimal totalSharesIssued = BigDecimal.ZERO;

        for (BvstPendingDeposit pd : deposits) {
            BigDecimal userSlippage = pd.getUsdAmount().multiply(slippageRate);
            BigDecimal effectiveContribution = pd.getUsdAmount().subtract(userSlippage);
            BigDecimal shares = effectiveContribution.divide(strike.getSharePriceAtStrike(), 8, RoundingMode.HALF_UP);

            pd.process(shares, effectiveContribution, userSlippage);
            issueSharesToUser(pd.getUserId(), shares, strike.getStrikeId(), strike.getSharePriceAtStrike());
            totalSharesIssued = totalSharesIssued.add(shares);
        }

        // Pending Withdraw 처리 (비슷한 패턴)
        // ...

        strike.setTotalSharesIssued(totalSharesIssued);
        strike.setDepositsProcessed(deposits.size());

        BvstPool pool = poolRepository.findById(strike.getPoolId()).orElseThrow();
        pool.issueShares(totalSharesIssued, totalDepositCash);
        // ... burn shares for withdraws
        poolRepository.save(pool);
    }
}