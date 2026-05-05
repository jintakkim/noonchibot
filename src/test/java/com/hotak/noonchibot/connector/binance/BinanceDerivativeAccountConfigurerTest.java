package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.derivative.*;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceDerivativeAccountConfigurerTest
        extends AbstractDerivativeAccountConfigurerTest<BinanceDerivativeAccountConfigurer> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RestAssistant mockRestAssistant;
    private TradingPairSymbolRegistry symbolRegistry;

    @Override
    protected BinanceDerivativeAccountConfigurer createConfigurer(
            DerivativeInfoTracker tracker, ExchangeEventPublisher publisher) {
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
        return new BinanceDerivativeAccountConfigurer(
                tracker, publisher, mockRestAssistant, symbolRegistry, Runnable::run);
    }

    @Override
    protected String tradingPair() {
        return "BTC-USDT";
    }

    // ===== Binance 응답 빌더 =====

    private JsonNode binanceOk() {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("code", 200);
        r.put("msg", "success");
        return r;
    }

    private JsonNode binanceLeverageOk(String symbol, int leverage) {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("symbol", symbol);
        r.put("leverage", leverage);
        r.put("maxNotionalValue", "1000000");
        return r;
    }

    private JsonNode binanceCodeMsg(int code, String msg) {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("code", code);
        r.put("msg", msg);
        return r;
    }

    @Test
    @DisplayName("HEDGE: dualSidePosition=true")
    void positionModeHedge() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceOk());

        configurer.ensurePositionMode(PositionMode.HEDGE).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        assertThat(captor.getValue().pathUrl())
                .isEqualTo(DerivativeApiSpec.POSITION_MODE_PATH_URL);
        assertThat(captor.getValue().params()).containsEntry("dualSidePosition", "true");
    }

    @Test
    @DisplayName("ONEWAY: dualSidePosition=false")
    void positionModeOneway() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.HEDGE));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceOk());

        configurer.ensurePositionMode(PositionMode.ONEWAY).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params()).containsEntry("dualSidePosition", "false");
    }

    @Test
    @DisplayName("Position mode -4059 응답은 멱등 성공으로 처리")
    void positionModeNoChangeIsSuccess() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceCodeMsg(
                        DerivativeApiSpec.NO_NEED_TO_CHANGE_POSITION_SIDE,
                        "No need to change position side."));

        configurer.ensurePositionMode(PositionMode.HEDGE).join();
    }

    @Test
    @DisplayName("활성 포지션 -4068이면 IllegalStateException")
    void positionModeActivePositionRejected() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceCodeMsg(-4068,
                        "Position side cannot be changed if there exists position."));

        assertThatThrownBy(() -> configurer.ensurePositionMode(PositionMode.HEDGE).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Leverage가 query param으로 String 전송")
    void leverageWireFormat() {
        when(mockTracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(5));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceLeverageOk("BTCUSDT", 10));

        configurer.ensureLeverage("BTC-USDT", 10).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        assertThat(captor.getValue().pathUrl()).isEqualTo(DerivativeApiSpec.LEVERAGE_PATH_URL);
        assertThat(captor.getValue().params())
                .containsEntry("symbol", "BTCUSDT")
                .containsEntry("leverage", "10");
    }

    @Test
    @DisplayName("Leverage 응답에 leverage 필드 없으면 예외")
    void throwsWhenLeverageResponseInvalid() {
        when(mockTracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceCodeMsg(-4028, "Invalid leverage"));

        assertThatThrownBy(() -> configurer.ensureLeverage("BTC-USDT", 200).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("CROSS는 marginType=CROSSED로 매핑 (CROSS 아님)")
    void crossMapsToCrossed() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.ISOLATED));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceOk());

        configurer.ensureMarginMode("BTC-USDT", MarginMode.CROSS).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        assertThat(captor.getValue().pathUrl())
                .isEqualTo(DerivativeApiSpec.MARGIN_TYPE_PATH_URL);
        assertThat(captor.getValue().params())
                .containsEntry("symbol", "BTCUSDT")
                .containsEntry("marginType", "CROSSED");
    }

    @Test
    @DisplayName("ISOLATED는 marginType=ISOLATED")
    void isolatedMapsToIsolated() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.CROSS));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceOk());

        configurer.ensureMarginMode("BTC-USDT", MarginMode.ISOLATED).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params()).containsEntry("marginType", "ISOLATED");
    }

    @Test
    @DisplayName("Margin -4046 응답은 멱등 성공으로 처리")
    void marginModeNoChangeIsSuccess() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceCodeMsg(
                        DerivativeApiSpec.NO_NEED_TO_CHANGE_MARGIN_TYPE,
                        "No need to change margin type."));

        configurer.ensureMarginMode("BTC-USDT", MarginMode.ISOLATED).join();
    }

    @Test
    @DisplayName("활성 포지션 -4048이면 IllegalStateException")
    void marginModeActivePositionRejected() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.of(MarginMode.CROSS));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceCodeMsg(-4048,
                        "Margin type cannot be changed if there exists position."));

        assertThatThrownBy(() -> configurer.ensureMarginMode("BTC-USDT", MarginMode.ISOLATED).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("페어가 거래소 심볼로 변환되어 호출된다")
    void convertsTradingPairToExchangeSymbol() {
        when(mockTracker.findLeverage("ETH-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(binanceLeverageOk("ETHUSDT", 20));

        configurer.ensureLeverage("ETH-USDT", 20).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().params())
                .containsEntry("symbol", "ETHUSDT")
                .containsEntry("leverage", "20");
    }
}