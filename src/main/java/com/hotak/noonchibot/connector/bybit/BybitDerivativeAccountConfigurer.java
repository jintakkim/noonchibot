package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.AbstractDerivativeAccountConfigurer;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.derivative.PositionMode;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BybitDerivativeAccountConfigurer extends AbstractDerivativeAccountConfigurer {
    private static final int POSITION_MODE_ONEWAY = 0;
    private static final int POSITION_MODE_HEDGE = 3;

    private final IoExecutor ioExecutor;
    private final RestAssistantImpl restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    public BybitDerivativeAccountConfigurer(
            DerivativeInfoTracker tracker,
            ExchangeEventPublisher eventPublisher,
            IoExecutor ioExecutor,
            RestAssistantImpl restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
            ) {
        super(tracker, eventPublisher);
        this.ioExecutor = ioExecutor;
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
    }

    @Override
    protected CompletableFuture<Void> applyPositionMode(PositionMode mode) {
        int modeApiValue = (mode == PositionMode.HEDGE) ? POSITION_MODE_HEDGE : POSITION_MODE_ONEWAY;
        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                        RestRequest.builder()
                                .method(HttpMethod.POST)
                                .pathUrl(DerivativeApiSpec.POSITION_MODE_PATH_URL)
                                .body(Map.of(
                                        "category", DerivativeApiSpec.CATEGORY_LINEAR,
                                        "coin", "USDT",  // linear는 settleCoin 단위로 적용
                                        "mode", modeApiValue
                                ))
                                .authRequired(true)
                                .throwError(false)
                                .build()
                )
        ).thenAccept(response -> {
            int retCode = response.has("retCode") ? response.get("retCode").asInt() : -1;

            if (retCode == DerivativeApiSpec.SUCCESS_CODE || retCode == DerivativeApiSpec.POSITION_MODE_HAS_NOT_BEEN_MODIFIED_CODE) {
                log.info("Position mode applied: {}", mode);
                return;
            }
            throw new IllegalStateException("Position mode change failed:" + response);
        });
    }


    @Override
    protected CompletableFuture<Void> applyLeverage(String tradingPair, int desired) {
        String exchangeSymbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        String leverageStr = String.valueOf(desired);

        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                        RestRequest.builder()
                                .method(HttpMethod.POST)
                                .pathUrl(DerivativeApiSpec.LEVERAGE_PATH_URL)
                                .body(Map.of(
                                        "category", DerivativeApiSpec.CATEGORY_LINEAR,
                                        "symbol", exchangeSymbol,
                                        "buyLeverage", leverageStr,
                                        "sellLeverage", leverageStr
                                ))
                                .authRequired(true)
                                .throwError(false)
                                .build()
                )
        ).thenAccept(response -> {
            int retCode = response.has("retCode") ? response.get("retCode").asInt() : -1;
            if (retCode == DerivativeApiSpec.SUCCESS_CODE || retCode == DerivativeApiSpec.SET_LEVERAGE_HAS_NOT_BEEN_MODIFIED_CODE) {
                log.info("Leverage applied: {} -> {}x", tradingPair, desired);
                return;
            }
            throw new IllegalStateException("Leverage change failed: " + response);
        });
    }

    @Override
    protected CompletableFuture<Void> applyMarginMode(String tradingPair, MarginMode mode) {
        String desired = toBybitMarginMode(mode);
        return ioExecutor.submitCompletable(this::fetchCurrentMarginMode)
                .thenCompose(current -> {
                    if (desired.equals(current)) {
                        log.info("Bybit account margin mode already {} (tradingPair {} ignored — account-wide setting), skipping", desired, tradingPair);
                        return CompletableFuture.completedFuture(null);
                    }
                    log.info("Bybit account margin mode change: {} -> {} (triggered by tradingPair {})", current, desired, tradingPair);
                    return doSetMarginMode(desired);
                });
    }

    private String fetchCurrentMarginMode() {
        JsonNode response = restAssistant.executeRequestAndGetJsonBody(
                RestRequest.builder()
                        .method(HttpMethod.GET)
                        .pathUrl(DerivativeApiSpec.ACCOUNT_INFO_PATH_URL)
                        .authRequired(true)
                        .throwError(false)
                        .build()
        );
        int retCode = response.has("retCode") ? response.get("retCode").asInt() : -1;
        if (retCode != DerivativeApiSpec.SUCCESS_CODE) {
            throw new IllegalStateException("Failed to fetch account info: " + response);
        }
        JsonNode result = response.get("result");
        if (result == null || !result.has("marginMode")) {
            throw new IllegalStateException("Account info response missing marginMode: " + response);
        }
        return result.get("marginMode").asString();
    }

    private CompletableFuture<Void> doSetMarginMode(String desired) {
        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                        RestRequest.builder()
                                .method(HttpMethod.POST)
                                .pathUrl(DerivativeApiSpec.MARGIN_MODE_PATH_URL)
                                .body(Map.of("setMarginMode", desired))
                                .authRequired(true)
                                .throwError(false)
                                .build()
                )
        ).thenAccept(response -> {
            int retCode = response.has("retCode") ? response.get("retCode").asInt() : -1;
            if (retCode == DerivativeApiSpec.SUCCESS_CODE) {
                log.info("Bybit account margin mode set to {}", desired);
                return;
            }
            throw new IllegalStateException("Set margin mode failed: " + response);
        });
    }

    private static String toBybitMarginMode(MarginMode mode) {
        return switch (mode) {
            case ISOLATED -> "ISOLATED_MARGIN";
            case CROSS    -> "REGULAR_MARGIN";
        };
    }
}
