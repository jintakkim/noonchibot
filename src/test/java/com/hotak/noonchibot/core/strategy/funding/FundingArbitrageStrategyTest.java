package com.hotak.noonchibot.core.strategy.funding;

import com.hotak.noonchibot.core.strategy.snapshot.StrategySnapshot;
import com.hotak.noonchibot.core.strategy.model.TargetOrderStyle;
import com.hotak.noonchibot.core.strategy.exposure.VenueExposureCalculator;
import com.hotak.noonchibot.core.strategy.model.VenuePosition;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyAccountView;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyContext;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyDecision;
import com.hotak.noonchibot.core.strategy.api.VenueStrategyMarketView;
import com.hotak.noonchibot.core.strategy.model.VenueTargetPosition;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.Position;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundingArbitrageStrategyTest {
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    @Test
    @DisplayName("초기에는 양쪽 거래소에 slice 크기만큼 long/short target을 만든다")
    void onTick_withoutExposure_targetsFirstSliceOnBothLegs() {
        List<StrategySnapshot> snapshots = new ArrayList<>();
        FundingArbitrageStrategy strategy = new FundingArbitrageStrategy(config("0.2", "0.2"), new VenueExposureCalculator());

        VenueStrategyDecision decision = strategy.onTick(context(List.of(), snapshots));

        VenueStrategyDecision.Targets targets = (VenueStrategyDecision.Targets) decision;
        assertThat(targets.positions()).containsExactly(
                new VenueTargetPosition(
                        "funding-arb",
                        Exchange.BINANCE_DERIVATIVE,
                        "BTC-USDT",
                        PositionSide.LONG,
                        new BigDecimal("0.2"),
                        TargetOrderStyle.market(),
                        NOW.plusSeconds(5),
                        "funding arbitrage long leg"
                ),
                new VenueTargetPosition(
                        "funding-arb",
                        Exchange.HYPERLIQUID_DERIVATIVE,
                        "BTC-USDC",
                        PositionSide.SHORT,
                        new BigDecimal("-0.2"),
                        TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                        NOW.plusSeconds(5),
                        "funding arbitrage short leg"
                )
        );
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().metrics())
                .containsEntry("targetBaseAmount", new BigDecimal("1.0"))
                .containsEntry("unbalancedLegHandling", UnbalancedLegHandling.AGGRESSIVELY_COMPLETE_OTHER_LEG.name());
    }

    @Test
    @DisplayName("이미 잡힌 노출이 있으면 현재 노출에서 한 slice만큼만 더 진행한다")
    void onTick_withExistingExposure_movesOneSliceTowardFinalTarget() {
        FundingArbitrageStrategy strategy = new FundingArbitrageStrategy(config("0.3", "0.3"), new VenueExposureCalculator());
        List<VenuePosition> positions = List.of(
                venuePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.3"),
                venuePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.3")
        );

        VenueStrategyDecision.Targets targets = (VenueStrategyDecision.Targets) strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(targets.positions().get(0).targetBaseAmount()).isEqualByComparingTo("0.6");
        assertThat(targets.positions().get(1).targetBaseAmount()).isEqualByComparingTo("-0.6");
    }

    @Test
    @DisplayName("최종 target을 넘지 않고 cap에서 멈춘다")
    void onTick_nearFinalTarget_capsAtTotalBaseAmount() {
        FundingArbitrageStrategy strategy = new FundingArbitrageStrategy(config("0.4", "0.4"), new VenueExposureCalculator());
        List<VenuePosition> positions = List.of(
                venuePosition(Exchange.BINANCE_DERIVATIVE, "BTC-USDT", PositionSide.LONG, "0.8"),
                venuePosition(Exchange.HYPERLIQUID_DERIVATIVE, "BTC-USDC", PositionSide.SHORT, "0.8")
        );

        VenueStrategyDecision.Targets targets = (VenueStrategyDecision.Targets) strategy.onTick(context(positions, new ArrayList<>()));

        assertThat(targets.positions().get(0).targetBaseAmount()).isEqualByComparingTo("1.0");
        assertThat(targets.positions().get(1).targetBaseAmount()).isEqualByComparingTo("-1.0");
    }

    @Test
    @DisplayName("진입 조건이 막으면 주문 target 없이 snapshot만 남긴다")
    void onTick_whenEntryConditionRejects_returnsNoop() {
        FundingArbitrageConfig config = new FundingArbitrageConfig(
                "funding-arb",
                longLeg("0.2"),
                shortLeg("0.2"),
                new BigDecimal("1.0"),
                context -> false,
                UnbalancedLegHandling.WAIT_FOR_OTHER_LEG
        );
        FundingArbitrageStrategy strategy = new FundingArbitrageStrategy(config, new VenueExposureCalculator());
        List<StrategySnapshot> snapshots = new ArrayList<>();

        VenueStrategyDecision decision = strategy.onTick(context(List.of(), snapshots));

        assertThat(decision).isEqualTo(new VenueStrategyDecision.Noop("entry condition rejected"));
        assertThat(snapshots).hasSize(1);
    }

    private FundingArbitrageConfig config(String longSlice, String shortSlice) {
        return new FundingArbitrageConfig(
                "funding-arb",
                longLeg(longSlice),
                shortLeg(shortSlice),
                new BigDecimal("1.0"),
                new AlwaysEnterCondition(),
                UnbalancedLegHandling.AGGRESSIVELY_COMPLETE_OTHER_LEG
        );
    }

    private FundingArbitrageLeg longLeg(String slice) {
        return new FundingArbitrageLeg(
                "long-a",
                Exchange.BINANCE_DERIVATIVE,
                "BTC-USDT",
                PositionSide.LONG,
                new LegExecutionPolicy(
                        TargetOrderStyle.market(),
                        new BigDecimal(slice),
                        1,
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

    private FundingArbitrageLeg shortLeg(String slice) {
        return new FundingArbitrageLeg(
                "short-b",
                Exchange.HYPERLIQUID_DERIVATIVE,
                "BTC-USDC",
                PositionSide.SHORT,
                new LegExecutionPolicy(
                        TargetOrderStyle.limit(new BigDecimal("50000"), TimeInForce.GTC, true),
                        new BigDecimal(slice),
                        1,
                        Duration.ofSeconds(5),
                        new RateFeeModel(FeeRateSchedule.makerTaker(new BigDecimal("0.0001"), new BigDecimal("0.00035")))
                )
        );
    }

    private VenuePosition venuePosition(
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            String amount
    ) {
        return new VenuePosition(
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

    private VenueStrategyContext context(
            List<VenuePosition> positions,
            List<StrategySnapshot> snapshots
    ) {
        return new VenueStrategyContext(
                NOW,
                new VenueStrategyMarketView() {},
                new VenueStrategyAccountView() {},
                () -> List.of(),
                () -> positions,
                snapshots::add
        );
    }
}
