package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.RestAssistantTestUtils;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.derivative.*;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class BybitDerivativeAccountConfigurerTest extends AbstractDerivativeAccountConfigurerTest<BybitDerivativeAccountConfigurer> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private RestAssistant mockRestAssistant;
    private TradingPairSymbolRegistry symbolRegistry;

    @Override
    protected BybitDerivativeAccountConfigurer createConfigurer(
            DerivativeInfoTracker tracker, ExchangeEventPublisher publisher) {
        mockRestAssistant = Mockito.mock(RestAssistant.class);
        symbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of(
                "BTC-USDT", "BTCUSDT",
                "ETH-USDT", "ETHUSDT"
        ));
        return new BybitDerivativeAccountConfigurer(
                tracker, publisher, Runnable::run,
                mockRestAssistant, symbolRegistry);
    }

    @Override
    protected String tradingPair() {
        return "BTC-USDT";
    }

    private JsonNode bybitOk() {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("retCode", DerivativeApiSpec.SUCCESS_CODE);
        r.put("retMsg", "OK");
        r.set("result", objectMapper.createObjectNode());
        r.put("time", System.currentTimeMillis());
        return r;
    }

    private JsonNode bybitError(int retCode, String msg) {
        ObjectNode r = objectMapper.createObjectNode();
        r.put("retCode", retCode);
        r.put("retMsg", msg);
        r.set("result", objectMapper.createObjectNode());
        r.put("time", System.currentTimeMillis());
        return r;
    }

    private JsonNode bybitAccountInfo(String marginMode) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("marginMode", marginMode);
        result.put("unifiedMarginStatus", 4);

        ObjectNode r = objectMapper.createObjectNode();
        r.put("retCode", DerivativeApiSpec.SUCCESS_CODE);
        r.put("retMsg", "OK");
        r.set("result", result);
        return r;
    }

    private boolean isAccountInfo(RestRequest req) {
        return req.method() == HttpMethod.GET
                && DerivativeApiSpec.ACCOUNT_INFO_PATH_URL.equals(req.pathUrl());
    }

    private boolean isSetMarginMode(RestRequest req) {
        return req.method() == HttpMethod.POST
                && DerivativeApiSpec.MARGIN_MODE_PATH_URL.equals(req.pathUrl());
    }

    @Test
    @DisplayName("HEDGE 모드 변경 시 mode=3, coin=USDT, category=linear로 호출")
    void positionModeHedge() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitOk());

        configurer.ensurePositionMode(PositionMode.HEDGE).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        assertThat(captor.getValue().pathUrl())
                .isEqualTo(DerivativeApiSpec.POSITION_MODE_PATH_URL);
        assertThat(captor.getValue().body())
                .asInstanceOf(MAP)
                .containsEntry("category", "linear")
                .containsEntry("coin", "USDT")
                .containsEntry("mode", 3);
    }

    @Test
    @DisplayName("ONEWAY 모드 변경 시 mode=0")
    void positionModeOneway() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.HEDGE));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitOk());

        configurer.ensurePositionMode(PositionMode.ONEWAY).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().body()).asInstanceOf(MAP).containsEntry("mode", 0);
    }

    @Test
    @DisplayName("Position mode 110025 응답은 멱등 성공으로 처리")
    void positionModeNotModifiedIsTreatedAsSuccess() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitError(
                        DerivativeApiSpec.POSITION_MODE_HAS_NOT_BEEN_MODIFIED_CODE,
                        "Position mode is not modified"));

        configurer.ensurePositionMode(PositionMode.HEDGE).join();
    }

    @Test
    @DisplayName("활성 포지션 거부 시 IllegalStateException")
    void positionModeActivePositionRejected() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.of(PositionMode.ONEWAY));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitError(110024,
                        "You have an existing position, so the position mode cannot be switched."));

        assertThatThrownBy(() -> configurer.ensurePositionMode(PositionMode.HEDGE).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Leverage 변경 시 buyLeverage=sellLeverage, String 타입 (UTA)")
    void leverageWireFormat() {
        when(mockTracker.findLeverage("BTC-USDT")).thenReturn(Optional.of(5));
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitOk());

        configurer.ensureLeverage("BTC-USDT", 10).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());

        assertThat(captor.getValue().pathUrl()).isEqualTo(DerivativeApiSpec.LEVERAGE_PATH_URL);
        assertThat(captor.getValue().body())
                .asInstanceOf(MAP)
                .containsEntry("category", "linear")
                .containsEntry("symbol", "BTCUSDT")
                .containsEntry("buyLeverage", "10")
                .containsEntry("sellLeverage", "10");
    }

    @Test
    @DisplayName("Leverage가 String으로 전송된다")
    void leverageSentAsString() {
        when(mockTracker.findLeverage("BTC-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitOk());

        configurer.ensureLeverage("BTC-USDT", 20).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().body())
                .asInstanceOf(MAP)
                .extractingByKey("buyLeverage")
                .isInstanceOf(String.class)
                .isEqualTo("20");
    }


    @Test
    @DisplayName("account-info가 다른 모드면 set-margin-mode로 변경")
    void marginModeReadThenWrite() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());
        RestAssistantTestUtils.stubByPath(mockRestAssistant,
                Map.entry(this::isAccountInfo, bybitAccountInfo("REGULAR_MARGIN")),
                Map.entry(this::isSetMarginMode, bybitOk())
        );
        configurer.ensureMarginMode("BTC-USDT", MarginMode.ISOLATED).join();

        InOrder order = inOrder(mockRestAssistant);
        order.verify(mockRestAssistant).executeRequestAndGetJsonBody(argThat(this::isAccountInfo));

        ArgumentCaptor<RestRequest> setCaptor = ArgumentCaptor.forClass(RestRequest.class);
        order.verify(mockRestAssistant).executeRequestAndGetJsonBody(setCaptor.capture());
        assertThat(setCaptor.getValue().pathUrl())
                .isEqualTo(DerivativeApiSpec.MARGIN_MODE_PATH_URL);
        assertThat(setCaptor.getValue().body())
                .asInstanceOf(MAP)
                .containsEntry("setMarginMode", "ISOLATED_MARGIN");
    }

    @Test
    @DisplayName("account-info가 이미 같은 모드면 set-margin-mode 호출 안 함 (멱등)")
    void marginModeReadOnlyWhenSame() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(argThat(this::isAccountInfo)))
                .thenReturn(bybitAccountInfo("ISOLATED_MARGIN"));

        configurer.ensureMarginMode("BTC-USDT", MarginMode.ISOLATED).join();

        verify(mockRestAssistant, never())
                .executeRequestAndGetJsonBody(argThat(this::isSetMarginMode));
    }

    @Test
    @DisplayName("CROSS 요청 시 setMarginMode=REGULAR_MARGIN 매핑")
    void crossMapsToRegularMargin() {
        when(mockTracker.findMarginMode("BTC-USDT")).thenReturn(Optional.empty());
        RestAssistantTestUtils.stubByPath(mockRestAssistant,
                Map.entry(this::isAccountInfo,   bybitAccountInfo("ISOLATED_MARGIN")),
                Map.entry(this::isSetMarginMode, bybitOk())
        );
        configurer.ensureMarginMode("BTC-USDT", MarginMode.CROSS).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant, times(2)).executeRequestAndGetJsonBody(captor.capture());

        List<RestRequest> calls = captor.getAllValues();
        assertThat(calls).hasSize(2);
        assertThat(calls.get(0).pathUrl()).isEqualTo(DerivativeApiSpec.ACCOUNT_INFO_PATH_URL);
        assertThat(calls.get(1).pathUrl()).isEqualTo(DerivativeApiSpec.MARGIN_MODE_PATH_URL);

        // captor의 마지막 호출(set)이 REGULAR_MARGIN을 보내야 함
        RestRequest setReq = captor.getAllValues().stream()
                .filter(this::isSetMarginMode).findFirst().orElseThrow();
        assertThat(setReq.body())
                .asInstanceOf(MAP)
                .containsEntry("setMarginMode", "REGULAR_MARGIN");
    }

    @Test
    @DisplayName("페어가 거래소 심볼로 변환되어 호출된다")
    void convertsTradingPairToExchangeSymbol() {
        when(mockTracker.findLeverage("ETH-USDT")).thenReturn(Optional.empty());
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(bybitOk());

        configurer.ensureLeverage("ETH-USDT", 20).join();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(mockRestAssistant).executeRequestAndGetJsonBody(captor.capture());
        assertThat(captor.getValue().body()).asInstanceOf(MAP).containsEntry("symbol", "ETHUSDT");
    }

    @Test
    @DisplayName("응답에 retCode 필드가 없으면 예외")
    void throwsWhenResponseHasNoRetCode() {
        when(mockTracker.findPositionMode()).thenReturn(Optional.empty());
        ObjectNode malformed = objectMapper.createObjectNode();
        malformed.put("unexpected", "data");
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(malformed);

        assertThatThrownBy(() -> configurer.ensurePositionMode(PositionMode.HEDGE).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}