package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.TestTaskScheduler;
import com.hotak.noonchibot.core.trade.TradingRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HLTradingRuleRegistryTest extends RestClientTest {
    private TestTaskScheduler taskScheduler;
    private HLTradingRuleRegistry registry;

    @BeforeEach
    void setUp() {
        taskScheduler = new TestTaskScheduler();
        registry = new HLTradingRuleRegistry(
                restAssistant,
                HyperliquidFixture.BTC_ETH_REGISTRY,
                taskScheduler
        );
    }

    @Test
    @DisplayName("meta 응답으로 trading rule과 asset meta를 함께 캐싱한다")
    void update_cachesTradingRulesAndAssetMetas() {
        runWith(HyperliquidFixture.metaSuccess(), () -> registry.update());

        TradingRule rule = registry.getTradingRule("BTC-USDC");
        assertThat(rule.minOrderSize()).isEqualByComparingTo(new BigDecimal("0.00001"));
        assertThat(rule.buyOrderCollateralToken()).isEqualTo("USDC");
        assertThat(registry.getAssetMeta("BTC-USDC").assetId()).isZero();
    }

    @Test
    @DisplayName("시작 시 meta를 조회하고 주기 갱신 task를 등록한다")
    void onStart_fetchesMetaAndSchedulesUpdate() {
        runWith(HyperliquidFixture.metaSuccess(), () -> registry.onStart());

        assertThat(registry.getTradingRule("ETH-USDC")).isNotNull();
        assertThat(taskScheduler.onlyScheduledTask().period()).isEqualTo(DerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL);
    }
}
