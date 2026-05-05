package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.derivative.AbstractDerivativeAccountConfigurer;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.MarginMode;
import com.hotak.noonchibot.core.derivative.PositionMode;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class BinanceDerivativeAccountConfigurer extends AbstractDerivativeAccountConfigurer {
    private final RestAssistant restAssistant;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private final IoExecutor ioExecutor;

    public BinanceDerivativeAccountConfigurer(
            DerivativeInfoTracker tracker,
            ExchangeEventPublisher eventPublisher,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            IoExecutor ioExecutor

    ) {
        super(tracker, eventPublisher);
        this.restAssistant = restAssistant;
        this.tradingPairSymbolRegistry = tradingPairSymbolRegistry;
        this.ioExecutor = ioExecutor;
    }

    @Override
    protected CompletableFuture<Void> applyPositionMode(PositionMode mode) {
        // dualSidePosition: "true" -> HEDGE, "false" -> ONEWAY
        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                        RestRequest.builder()
                                .method(HttpMethod.POST)
                                .pathUrl(DerivativeApiSpec.POSITION_MODE_PATH_URL)
                                .params(Map.of(
                                        "dualSidePosition", String.valueOf(mode == PositionMode.HEDGE)
                                ))
                                .authRequired(true)
                                .throwError(false)
                                .build()
                )
        ).thenAccept(response -> {
            // 포지션이 변경되었더라면 {"code":200,"msg":"success"}
            // 이미 같은 모드면 {"code":-4059,"msg":"No need to change position side."}
            int code = response.has("code") ? response.get("code").asInt() : 200;
            if (code != 200 && code != DerivativeApiSpec.NO_NEED_TO_CHANGE_POSITION_SIDE) {
                throw new IllegalStateException("Position mode change failed: " + response);
            }
            log.info("Position mode applied: {}", mode);
        });
    }

    @Override
    protected CompletableFuture<Void> applyLeverage(String tradingPair, int desired) {
        String exchangeSymbol = tradingPairSymbolRegistry
                .convertTradingPairToExchangeSymbol(tradingPair);

        // POST /fapi/v1/leverage
        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(DerivativeApiSpec.LEVERAGE_PATH_URL)
                            .params(Map.of(
                                    "symbol", exchangeSymbol,
                                    "leverage", String.valueOf(desired)
                            ))
                            .authRequired(true)
                            .build()
                )
        ).thenAccept(response -> {
            // {"leverage":10,"maxNotionalValue":"1000000","symbol":"BTCUSDT"}
            if (!response.has("leverage")) {
                throw new IllegalStateException("Leverage change failed: " + response);
            }
            log.info("Leverage applied: {} -> {}x", tradingPair, desired);
        });
    }

    @Override
    protected CompletableFuture<Void> applyMarginMode(String tradingPair, MarginMode mode) {
        String exchangeSymbol = tradingPairSymbolRegistry
                .convertTradingPairToExchangeSymbol(tradingPair);
        String marginType = switch (mode) {
            case CROSS -> "CROSSED";
            case ISOLATED -> "ISOLATED";
        };
        return ioExecutor.submitCompletable(
                () -> restAssistant.executeRequestAndGetJsonBody(
                    RestRequest.builder()
                            .method(HttpMethod.POST)
                            .pathUrl(DerivativeApiSpec.MARGIN_TYPE_PATH_URL)
                            .params(Map.of(
                                    "symbol", exchangeSymbol,
                                    "marginType", marginType
                            ))
                            .authRequired(true)
                            .build()
                )
        ).thenAccept(response -> {
            // 정상: {"code":200,"msg":"success"}
            // 이미 같은 모드: {"code":-4046,"msg":"No need to change margin type."}
            int code = response.has("code") ? response.get("code").asInt() : 200;
            if (code != 200 && code != DerivativeApiSpec.NO_NEED_TO_CHANGE_MARGIN_TYPE) {
                throw new IllegalStateException("Margin mode change failed: " + response);
            }
            log.info("Margin mode applied: {} -> {}", tradingPair, mode);
        });
    }
}