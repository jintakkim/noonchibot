package com.hotak.noonchibot.connector.hyperliquid;

import com.google.common.annotations.VisibleForTesting;
import com.hotak.noonchibot.connector.DefaultExchangeErrorClassifier;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingRuleRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestAssistantConfigurer;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import com.hotak.noonchibot.core.trade.TradingRule;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

class HLTradingRuleRegistry implements TradingRuleRegistry, LifecycleAware {
    private static final BigDecimal MIN_NOTIONAL_SIZE = new BigDecimal("10");

    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final TaskScheduler taskScheduler;
    private volatile Map<String, TradingRule> tradingRules = Map.of();
    private volatile Map<String, AssetMeta> assetMetas = Map.of();
    private volatile ScheduledFuture<?> scheduledFuture;

    public HLTradingRuleRegistry(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler
    ) {
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.taskScheduler = taskScheduler;
    }

    public HLTradingRuleRegistry(
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            TaskScheduler taskScheduler,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this(
                new RestAssistantConfigurer(restAssistant)
                        .circuit(circuitBreakerRegistry, CircuitBreakerNames.tradingRules(Exchange.HYPERLIQUID_DERIVATIVE))
                        .errorClassifier(new DefaultExchangeErrorClassifier())
                        .maxRetry(2)
                        .build(),
                tradingPairSymbolRegistry,
                taskScheduler
        );
    }

    @Override
    public TradingRule getTradingRule(String tradingPair) {
        return tradingRules.get(tradingPair);
    }

    public AssetMeta getAssetMeta(String tradingPair) {
        AssetMeta meta = assetMetas.get(tradingPair);
        if (meta == null) {
            throw new IllegalArgumentException("Unknown hyperliquid trading pair: " + tradingPair);
        }
        return meta;
    }

    @VisibleForTesting
    void update() {
        Map<String, AssetMeta> nextAssetMetas = parseAssetMetas(fetchMeta());
        assetMetas = nextAssetMetas;
        tradingRules = nextAssetMetas.values().stream()
                .filter(meta -> !meta.delisted())
                .map(this::toTradingRule)
                .collect(Collectors.toUnmodifiableMap(TradingRule::tradingPair, Function.identity()));
    }

    private JsonNode fetchMeta() {
        return restAssistant.executeRequestAndGetJsonBody(RestRequest.builder()
                .method(HttpMethod.POST)
                .pathUrl(DerivativeApiSpec.INFO_PATH_URL)
                .body(Map.of("type", "meta"))
                .build());
    }

    private Map<String, AssetMeta> parseAssetMetas(JsonNode metaResponse) {
        Map<String, AssetMeta> nextByPair = new HashMap<>();
        JsonNode universe = metaResponse.get("universe");
        for (int assetId = 0; assetId < universe.size(); assetId++) {
            JsonNode asset = universe.get(assetId);
            String coin = asset.get("name").asString();
            String tradingPair = tradingPairSymbolRegistry.convertExchangeSymbolToTradingPair(coin, false);
            if (tradingPair == null) continue;
            AssetMeta assetMeta = new AssetMeta(
                    assetId,
                    coin,
                    tradingPair,
                    asset.get("szDecimals").asInt(),
                    asset.has("maxLeverage") ? asset.get("maxLeverage").asInt() : 0,
                    asset.has("isDelisted") && asset.get("isDelisted").asBoolean()
            );
            nextByPair.put(tradingPair, assetMeta);
        }
        return Map.copyOf(nextByPair);
    }

    private TradingRule toTradingRule(AssetMeta meta) {
        return new TradingRule(
                meta.tradingPair(),
                meta.sizeIncrement(),
                null,
                null,
                meta.sizeIncrement(),
                MIN_NOTIONAL_SIZE,
                5,
                Set.of(OrderType.LIMIT),
                "USDC",
                "USDC"
        );
    }

    @Override
    public void onStart() {
        update();
        scheduledFuture = taskScheduler.scheduleAtFixedRate(this::update, DerivativeApiSpec.TRADING_RULE_UPDATE_INTERVAL);
    }

    @Override
    public void onShutdown() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    @Override
    public int phase() {
        return Phases.TRADING_RULE_SETUP;
    }

    record AssetMeta(
            int assetId,
            String coin,
            String tradingPair,
            int sizeDecimals,
            int maxLeverage,
            boolean delisted
    ) {
        BigDecimal sizeIncrement() {
            return BigDecimal.ONE.movePointLeft(sizeDecimals);
        }
    }
}
