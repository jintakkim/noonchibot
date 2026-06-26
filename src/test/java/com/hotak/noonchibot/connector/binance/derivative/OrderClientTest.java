package com.hotak.noonchibot.connector.binance.derivative;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.binance.OrderFixture;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.connector.web.testutils.RestClientTest;
import com.hotak.noonchibot.core.order.OrderCancelResult;
import com.hotak.noonchibot.core.order.OrderClient;
import com.hotak.noonchibot.core.order.OrderPlaceResult;
import com.hotak.noonchibot.core.order.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderClientTest extends RestClientTest {
    private TimeSynchronizer timeSynchronizer;
    private TradingPairSymbolRegistry symbolRegistry;
    private OrderClient orderClient;

    @BeforeEach
    void setUp() {
        timeSynchronizer = mock(TimeSynchronizer.class);
        symbolRegistry = mock(TradingPairSymbolRegistry.class);
        when(symbolRegistry.convertTradingPairToExchangeSymbol("BTC-USDT"))
                .thenReturn("BTCUSDT");
        when(timeSynchronizer.serverTime()).thenReturn(1_717_200_000_000L); //dummy value
        orderClient = new OrderClientImpl(
                timeSynchronizer,
                restAssistant,
                symbolRegistry
        );
    }

    @Test
    @DisplayName("지정가 주문이 성공하면 거래소 주문 ID, 주문 상태, 거래소 시간을 반환한다")
    void placeOrder_whenLimitOrderSucceeds_returnsOrderPlaceResult() {

        runWith(OrderFixture.btcUsdtLimitBuySuccess(), () -> {
            OrderPlaceResult result = orderClient.placeOrder(OrderFixture.btcUsdtLimitBuyOrder());
            assertThat(result.exchangeOrderId()).isNotNull();
            assertThat(result.orderState()).isEqualTo(OrderState.OPEN);
            assertThat(result.timestamp()).isEqualTo(Instant.ofEpochMilli(1_780_302_734_417L));
        });


    }

//    @Test
//    @DisplayName("PostOnly 지정가 주문이면 timeInForce를 GTX로 요청한다")
//    void placeOrder_whenPostOnlyLimitOrder_usesGtxTimeInForce() {
//        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
//                .thenReturn(orderResponse("12345", "NEW", 1_717_200_001_000L));
//
//        orderClient.placeOrder(limitOrder(true));
//
//        RestRequest request = captureRequest();
//        assertThat(request.method()).isEqualTo(HttpMethod.POST);
//        assertThat(request.pathUrl()).isEqualTo(DerivativeApiSpec.ORDER_PATH_URL);
//        assertThat(request.params()).containsEntry("symbol", "BTCUSDT");
//        assertThat(request.params()).containsEntry("side", "BUY");
//        assertThat(request.params()).containsEntry("type", "LIMIT");
//        assertThat(request.params()).containsEntry("timeInForce", "GTX");
//        assertThat(request.params()).containsEntry("newClientOrderId", "cid-1");
//    }

    @Test
    @DisplayName("바이낸스 503 Unknown error는 주문 생성 여부 미확정 상태로 반환한다")
    void placeOrder_whenBinanceUnknownExecution_returnsPendingCreateWithoutExchangeOrderId() {
        runWith(OrderFixture.btcUsdtLimitBuyUnknownError(), () -> {
            OrderPlaceResult result = orderClient.placeOrder(OrderFixture.btcUsdtLimitBuyOrder());
            assertThat(result.exchangeOrderId()).isNull();
            assertThat(result.orderState()).isEqualTo(OrderState.PENDING_CREATE);
        });
    }

    @Test
    @DisplayName("바이낸스 503 Unknown error가 아니면 예외를 그대로 전파한다")
    void placeOrder_whenOtherExchangeApiException_throws() {
        runWith(OrderFixture.btcUsdtLimitBuyMarginInsufficientBadRequest(), () -> {
            assertThatThrownBy(() -> orderClient.placeOrder(OrderFixture.btcUsdtLimitBuyOrder()))
                    .isInstanceOf(ExchangeApiException.class)
                    .hasMessageContaining("Margin is insufficient.");
        });
    }

    @Test
    @DisplayName("취소 요청이 성공하면 취소 확정 결과를 반환한다")
    void cancelOrder_whenRequestSucceeds_returnsFinalizedCancelResult() {
        runWith(OrderFixture.btcUsdtCancelSuccess(), () -> {
            OrderCancelResult result = orderClient.cancelOrder(
                    OrderFixture.TRADING_PAIR,
                    OrderFixture.CLIENT_ORDER_ID
            );
            assertThat(result.cancelFinalized()).isTrue();
            assertThat(result.timestamp()).isNotNull();
        });
    }
}
