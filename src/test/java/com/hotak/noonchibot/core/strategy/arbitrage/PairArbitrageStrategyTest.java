package com.hotak.noonchibot.core.strategy.arbitrage;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.exposure.ExposureCalculator;
import com.hotak.noonchibot.core.strategy.model.ExchangeOrderView;
import com.hotak.noonchibot.core.strategy.model.ExchangePosition;
import com.hotak.noonchibot.core.strategy.api.StrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.StrategyCondition;
import com.hotak.noonchibot.core.strategy.api.StrategyContext;
import com.hotak.noonchibot.core.strategy.execution.ExecutionPlan;
import com.hotak.noonchibot.core.strategy.execution.ExecutionCommand;
import com.hotak.noonchibot.core.strategy.execution.ExecutionUrgency;
import com.hotak.noonchibot.core.strategy.safety.TradingStatus;
import com.hotak.noonchibot.core.strategy.api.StrategyMarketView;
import com.hotak.noonchibot.core.strategy.arbitrage.condition.PriceGapCondition;
import com.hotak.noonchibot.core.strategy.arbitrage.fee.FeeRateSchedule;
import com.hotak.noonchibot.core.strategy.arbitrage.fee.RateFeeModel;
import com.hotak.noonchibot.core.strategy.arbitrage.funding.AverageFundingSpreadCondition;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.Position;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.OrderView;
import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshotSink;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PairArbitrageStrategyTest {
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    @DisplayName("초기에는 양쪽 거래소에 slice 크기만큼 long/short target을 만든다")
    void onTick_withoutExposure_targetsFirstSliceOnBothLegs() {
        List<StrategySnapshot> snapshots = new ArrayList<>();
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config("0.2"), new ExposureCalculator());

        ExecutionPlan plan = strategy.onTick(context(List.of(), snapshots));

        assertThat(submitOrders(plan)).hasSize(2);
        assertThat(submitOrders(plan).get(0).candidate().getAmount()).isEqualByComparingTo("0.2");
        assertThat(submitOrders(plan).get(0).candidate().getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(submitOrders(plan).get(1).candidate().getAmount()).isEqualByComparingTo("0.2");
        assertThat(submitOrders(plan).get(1).candidate().getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().metrics())
                .containsEntry("targetBaseAmount", new BigDecimal("1.0"))
                .containsEntry("entryMatched", true)
                .containsEntry("orderValid", true);
    }

    @Test
    @DisplayName("이미 잡힌 노출이 있으면 현재 노출에서 한 slice만큼만 더 진행한다")
    void onTick_withExistingExposure_movesOneSliceTowardFinalTarget() {
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config("0.3"), new ExposureCalculator());
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.3"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.3")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan)).extracting(command -> command.candidate().getAmount())
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.3"));
    }

    @Test
    @DisplayName("최종 target을 넘지 않고 cap에서 멈춘다")
    void onTick_nearFinalTarget_capsAtTotalBaseAmount() {
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config("0.4"), new ExposureCalculator());
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.8"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.8")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan)).extracting(command -> command.candidate().getAmount())
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.2"));
    }

    @Test
    @DisplayName("진입 조건이 막으면 주문 target 없이 snapshot만 남긴다")
    void onTick_whenEntryConditionRejects_returnsNoop() {
        PairArbitrageConfig config = new PairArbitrageConfig(
                "funding-arb",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                context -> false
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config, new ExposureCalculator());
        List<StrategySnapshot> snapshots = new ArrayList<>();

        ExecutionPlan plan = strategy.onTick(context(List.of(), snapshots));

        assertThat(plan.isEmpty()).isTrue();
        assertThat(snapshots).hasSize(1);
    }

    @Test
    @DisplayName("페어의 한 leg라도 활성 주문이 있으면 추가 target을 만들지 않는다")
    void onTick_withActiveOrder_waitsForPair() {
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config("0.2"), new ExposureCalculator());

        ExecutionPlan plan = strategy.onTick(context(
                List.of(),
                List.of(openOrder(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", TradeType.BUY, "0.2")),
                new ArrayList<>()
        ));

        assertThat(plan.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("주문 유효 조건을 이탈하면 활성 주문부터 취소한다")
    void onTick_whenOrderValidityIsLost_cancelsActiveOrderFirst() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                StrategyCondition.never(),
                StrategyCondition.never(),
                StrategyCondition.never()
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );

        ExecutionPlan plan = strategy.onTick(context(
                List.of(),
                List.of(openOrder(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", TradeType.BUY, "0.2")),
                new ArrayList<>()
        ));

        assertThat(plan.commands()).singleElement().isInstanceOf(ExecutionCommand.CancelOrder.class);
    }

    @Test
    @DisplayName("주문 유효 조건을 이탈하면 큰 leg를 줄인다")
    void onTick_whenOrderValidityConditionRejects_reducesLargerLeg() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                context -> false,
                context -> false,
                context -> false
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.5"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.2")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan)).singleElement().satisfies(command -> {
            assertThat(command.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
            assertThat(command.candidate().getAmount()).isEqualByComparingTo("0.2");
            assertThat(command.candidate().getTradeType()).isEqualTo(TradeType.SELL);
            assertThat(command.candidate().getReduceOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("entry 이탈 후에도 주문 유효 조건 안이면 부족한 leg를 늘린다")
    void onTick_betweenEntryAndOrderValidity_increasesDeficientLeg() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                context -> true,
                context -> false,
                context -> false
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.5"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.2")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan)).singleElement().satisfies(command -> {
            assertThat(command.exchange()).isEqualTo(Exchange.HYPERLIQUID_DERIVATIVE);
            assertThat(command.candidate().getAmount()).isEqualByComparingTo("0.2");
            assertThat(command.candidate().getTradeType()).isEqualTo(TradeType.SELL);
            assertThat(command.candidate().getReduceOnly()).isFalse();
        });
    }

    @Test
    @DisplayName("거래 중지 상태에서 불균형이면 큰 leg를 reduce-only로 줄인다")
    void onTick_whileTradingPaused_reducesLargerLeg() {
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(config("0.2"), new ExposureCalculator());
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.5"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.2")
        );
        StrategyContext context = new StrategyContext(
                NOW,
                StrategyMarketView.UNAVAILABLE,
                new StrategyAccountView() {},
                () -> List.of(),
                () -> positions,
                StrategySnapshotSink.NOOP,
                () -> TradingStatus.TRADING_PAUSED
        );

        ExecutionPlan plan = strategy.onTick(context);

        assertThat(submitOrders(plan)).singleElement().satisfies(command -> {
            assertThat(command.exchange()).isEqualTo(Exchange.BINANCE_DERIVATIVE);
            assertThat(command.candidate().getTradeType()).isEqualTo(TradeType.SELL);
            assertThat(command.candidate().getReduceOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("EXIT 조건이면 양쪽 포지션의 target을 0으로 만든다")
    void onTick_whenExitConditionMatches_closesBothLegs() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                context -> true,
                context -> false,
                context -> true
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.5"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.5")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan)).hasSize(2);
        assertThat(submitOrders(plan)).extracting(command -> command.candidate().getAmount())
                .allSatisfy(amount -> assertThat(amount).isEqualByComparingTo("0.5"));
    }

    @Test
    @DisplayName("EXIT 조건에서 오픈오더가 있으면 target 대신 취소 요청을 만든다")
    void onTick_whenExitMatchesWithOpenOrder_requestsCancellation() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                context -> true,
                context -> false,
                context -> true
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );

        ExecutionPlan plan = strategy.onTick(context(
                List.of(),
                List.of(openOrder(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", TradeType.BUY, "0.2")),
                new ArrayList<>()
        ));

        assertThat(plan.commands()).singleElement().isInstanceOf(ExecutionCommand.CancelOrder.class);
    }

    @Test
    @DisplayName("하나의 전략이 여러 페어의 진입 target을 같은 Tick에 만든다")
    void onTick_withMultiplePairs_evaluatesEachPairIndependently() {
        ArbitragePair btcPair = new ArbitragePair(
                "btc",
                longLeg("BTC-USDT"),
                shortLeg("BTC-USDC"),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                StrategyCondition.always(),
                StrategyCondition.always(),
                StrategyCondition.never()
        );
        ArbitragePair ethPair = new ArbitragePair(
                "eth",
                longLeg("ETH-USDT"),
                shortLeg("ETH-USDC"),
                new BigDecimal("2.0"),
                new BigDecimal("0.5"),
                StrategyCondition.always(),
                StrategyCondition.always(),
                StrategyCondition.never()
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(btcPair, ethPair)),
                new ExposureCalculator()
        );

        ExecutionPlan plan = strategy.onTick(context(List.of(), new ArrayList<>()));

        assertThat(submitOrders(plan))
                .extracting(command -> command.candidate().getTradingPair())
                .containsExactly("BTC-USDT", "BTC-USDC", "ETH-USDT", "ETH-USDC");
    }

    @Test
    @DisplayName("7일 평균 펀딩 스프레드와 실질 가격갭 조건을 조합해 진입한다")
    void onTick_withComposedMarketConditions_entersPair() {
        StrategyCondition<ArbitrageEvaluation> entryCondition = new AverageFundingSpreadCondition(
                Duration.ofDays(7),
                new BigDecimal("0.0005")
        ).and(PriceGapCondition.atLeast(new BigDecimal("0.01")));
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                StrategyCondition.always(),
                entryCondition,
                StrategyCondition.never(),
                StrategyCondition.never()
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );
        StrategyMarketView marketView = new StrategyMarketView() {
            @Override
            public BigDecimal averageFundingRate(Exchange exchange, String tradingPair, Duration window) {
                return exchange == Exchange.BINANCE_DERIVATIVE
                        ? new BigDecimal("0.001")
                        : new BigDecimal("0.002");
            }

            @Override
            public BigDecimal executablePrice(
                    Exchange exchange,
                    String tradingPair,
                    TradeType tradeType,
                    BigDecimal baseAmount
            ) {
                return tradeType == TradeType.BUY
                        ? new BigDecimal("100")
                        : new BigDecimal("110");
            }
        };

        ExecutionPlan plan = strategy.onTick(context(List.of(), List.of(), new ArrayList<>(), marketView));

        assertThat(plan.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("손절 조건은 일반 종료 조건보다 먼저 평가한다")
    void onTick_whenStopLossAndExitMatch_prioritizesStopLoss() {
        ArbitragePair pair = new ArbitragePair(
                "btc",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal("0.2"),
                StrategyCondition.always(),
                StrategyCondition.always(),
                StrategyCondition.always(),
                StrategyCondition.always()
        );
        PairArbitrageStrategy strategy = new PairArbitrageStrategy(
                new PairArbitrageConfig("funding-arb", List.of(pair)),
                new ExposureCalculator()
        );
        List<ExchangePosition> positions = List.of(
                exchangePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.5"),
                exchangePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.5")
        );

        ExecutionPlan plan = strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(submitOrders(plan))
                .extracting(ExecutionCommand.SubmitOrder::reason)
                .allMatch(reason -> reason.contains("stop loss"));
        assertThat(submitOrders(plan)).allSatisfy(command -> {
            assertThat(command.urgency()).isEqualTo(ExecutionUrgency.EMERGENCY);
            assertThat(command.candidate().getOrderType()).isEqualTo(OrderType.MARKET);
            assertThat(command.candidate().getReduceOnly()).isTrue();
        });
    }

    private PairArbitrageConfig config(String slice) {
        return new PairArbitrageConfig(
                "funding-arb",
                longLeg(),
                shortLeg(),
                new BigDecimal("1.0"),
                new BigDecimal(slice),
                StrategyCondition.always()
        );
    }

    private ArbitrageLeg longLeg() {
        return longLeg("BTC-USDT");
    }

    private ArbitrageLeg longLeg(String tradingPair) {
        return new ArbitrageLeg(
                "long-a",
                Exchange.BINANCE_DERIVATIVE,
                tradingPair,
                PositionSide.LONG,
                new LegExecutionPolicy(
                        TargetOrderStyle.market(),
                        Duration.ofSeconds(5),
                        new RateFeeModel(new FeeRateSchedule(
                                new BigDecimal("0.001"),
                                new BigDecimal("0.015"),
                                new BigDecimal("0.001"),
                                new BigDecimal("0.015")
                        ))
                )
        );
    }

    private ArbitrageLeg shortLeg() {
        return shortLeg("BTC-USDC");
    }

    private ArbitrageLeg shortLeg(String tradingPair) {
        return new ArbitrageLeg(
                "short-b",
                Exchange.HYPERLIQUID_DERIVATIVE,
                tradingPair,
                PositionSide.SHORT,
                new LegExecutionPolicy(
                        TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                        Duration.ofSeconds(5),
                        new RateFeeModel(FeeRateSchedule.makerTaker(new BigDecimal("0.0001"), new BigDecimal("0.00035")))
                )
        );
    }

    private ExchangePosition exchangePosition(
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            String amount
    ) {
        return new ExchangePosition(
                exchange,
                new Position(
                        tradingPair,
                        positionSide,
                        NOW.minusSeconds(60),
                        BigDecimal.ZERO,
                        new BigDecimal("50000"),
                        new BigDecimal(amount)
                )
        );
    }

    private List<ExecutionCommand.SubmitOrder> submitOrders(ExecutionPlan plan) {
        return plan.commands().stream()
                .filter(ExecutionCommand.SubmitOrder.class::isInstance)
                .map(ExecutionCommand.SubmitOrder.class::cast)
                .toList();
    }

    private StrategyContext context(
            List<ExchangePosition> positions,
            List<StrategySnapshot> snapshots
    ) {
        return context(positions, List.of(), snapshots);
    }

    private StrategyContext context(
            List<ExchangePosition> positions,
            List<ExchangeOrderView> orders,
            List<StrategySnapshot> snapshots
    ) {
        return context(positions, orders, snapshots, StrategyMarketView.UNAVAILABLE);
    }

    private StrategyContext context(
            List<ExchangePosition> positions,
            List<ExchangeOrderView> orders,
            List<StrategySnapshot> snapshots,
            StrategyMarketView marketView
    ) {
        return new StrategyContext(
                NOW,
                marketView,
                new StrategyAccountView() {},
                () -> orders,
                () -> positions,
                snapshots::add
        );
    }

    private ExchangeOrderView openOrder(
            Exchange exchange,
            String tradingPair,
            TradeType tradeType,
            String remainingAmount
    ) {
        return new ExchangeOrderView(
                exchange,
                new OrderView(
                        "client-order-id",
                        "exchange-order-id",
                        tradingPair,
                        OrderType.LIMIT,
                        tradeType,
                        TimeInForce.GTC,
                        true,
                        OrderState.OPEN,
                        new BigDecimal(remainingAmount),
                        new BigDecimal("50000"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        new BigDecimal(remainingAmount),
                        Set.of(),
                        Map.of(),
                        NOW,
                        NOW
                )
        );
    }
}
