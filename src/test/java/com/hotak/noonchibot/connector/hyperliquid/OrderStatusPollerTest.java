package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.ExchangeApiException;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.TestMainExecutor;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import com.hotak.noonchibot.core.datatype.WebsocketStatus;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderLostEvent;
import com.hotak.noonchibot.core.order.OrderUpdateDto;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderStatusPollerTest {
    private static ObjectMapper objectMapper = new ObjectMapper();

    private RestAssistantImpl restAssistant;
    private ExchangeEventPublisher eventPublisher;
    private OrderTracker orderTracker;
    private WebsocketStatus websocketStatus;
    private TaskScheduler scheduler;
    private TestMainExecutor mainExecutor;
    private IoExecutor ioExecutor;
    private DerivativeOrderStatusPoller poller;

    private static final String USER_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String TRADING_PAIR = "BTC-USDC";
    private static final String CLOID = "0xabc123def456abc123def456abc123de";

    @BeforeEach
    void setUp() {
        restAssistant = mock(RestAssistantImpl.class);
        eventPublisher = mock(ExchangeEventPublisher.class);
        orderTracker = mock(OrderTracker.class);
        websocketStatus = mock(WebsocketStatus.class);
        scheduler = mock(TaskScheduler.class);
        mainExecutor = new TestMainExecutor();
        ioExecutor = new VirtualThreadIoExecutor();
        objectMapper = new ObjectMapper();

        poller = new DerivativeOrderStatusPoller(
                restAssistant, eventPublisher, orderTracker,
                mainExecutor, ioExecutor, USER_ADDRESS,
                websocketStatus, scheduler
        );
    }

    @Test
    @DisplayName("추적 중인 주문이 없으면 REST 호출을 하지 않는다")
    void noOrdersToPoll_doesNotCallRest() {
        when(orderTracker.getAll()).thenReturn(List.of());

        poller.pollData();
        verifyNoInteractions(restAssistant);
        verifyNoInteractions(eventPublisher);
    }

    @Nested
    @DisplayName("정상 응답 처리")
    class NormalResponseHandling {

        @Test
        @DisplayName("open 상태 주문을 OrderUpdateEvent로 발행한다")
        void openOrder_publishesOpenEvent() {
            // given
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            String responseJson = """
                {
                  "status": "order",
                  "order": {
                    "order": {
                      "coin": "BTC",
                      "side": "B",
                      "limitPx": "50000.0",
                      "sz": "0.1",
                      "oid": 987654321,
                      "timestamp": 1700000000000,
                      "origSz": "0.1",
                      "cloid": "%s"
                    },
                    "status": "open",
                    "statusTimestamp": 1700000001000
                  }
                }
                """.formatted(CLOID);
            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(objectMapper.readTree(responseJson));

            // when
            poller.pollData().join();

            // then
            ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
            verify(eventPublisher).publish(captor.capture());

            OrderUpdateDto event = captor.getValue();
            assertThat(event.tradingPair()).isEqualTo(TRADING_PAIR);
            assertThat(event.clientOrderId()).isEqualTo(CLOID);
            assertThat(event.exchangeOrderId()).isEqualTo("987654321");
            assertThat(event.newState()).isEqualTo(OrderState.OPEN);
            assertThat(event.updateTimestamp()).isEqualTo(Instant.ofEpochMilli(1700000001000L));
        }

        @Test
        @DisplayName("filled 상태 주문을 FILLED로 매핑한다")
        void filledOrder_publishesFilledEvent() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("filled", 1700000001000L));

            poller.pollData().join();

            ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().newState()).isEqualTo(OrderState.FILLED);
        }

        @Test
        @DisplayName("canceled 상태 주문을 CANCELED로 매핑한다")
        void canceledOrder_publishesCanceledEvent() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("canceled", 1700000001000L));

            poller.pollData().join();

            ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().newState()).isEqualTo(OrderState.CANCELED);
        }

        @Test
        @DisplayName("marginCanceled, vaultWithdrawalCanceled, openInterestCapCanceled 모두 CANCELED로 매핑한다")
        void allCancelVariants_mapToCanceled() {
            List<String> cancelVariants = List.of(
                    "marginCanceled",
                    "vaultWithdrawalCanceled",
                    "openInterestCapCanceled"
            );

            for (String status : cancelVariants) {
                reset(restAssistant, eventPublisher, orderTracker);
                InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
                when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));
                when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                        .thenReturn(buildOrderResponse(status, 1700000001000L));

                poller.pollData().join();

                ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
                verify(eventPublisher).publish(captor.capture());
                assertThat(captor.getValue().newState())
                        .as("status=%s", status)
                        .isEqualTo(OrderState.CANCELED);
            }
        }

        @Test
        @DisplayName("rejected 상태를 FAILED로 매핑한다")
        void rejectedOrder_publishesFailedEvent() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("rejected", 1700000001000L));

            poller.pollData().join();

            ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().newState()).isEqualTo(OrderState.FAILED);
        }

        @Test
        @DisplayName("triggered 상태를 OPEN으로 매핑한다")
        void triggeredOrder_mapsToOpen() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("triggered", 1700000001000L));

            poller.pollData().join();

            ArgumentCaptor<OrderUpdateDto> captor = ArgumentCaptor.forClass(OrderUpdateDto.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().newState()).isEqualTo(OrderState.OPEN);
        }
    }

    @Nested
    @DisplayName("주문 못 찾음 처리")
    class OrderNotFoundHandling {
        @Test
        @DisplayName("unknownOid 응답이 오면 OrderLostEvent를 발행한다")
        void unknownOid_publishesLostEvent() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            String responseJson = """
                {"status": "unknownOid"}
                """;
            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(objectMapper.readTree(responseJson));

            poller.pollData().join();

            ArgumentCaptor<OrderLostEvent> captor = ArgumentCaptor.forClass(OrderLostEvent.class);
            verify(eventPublisher).publish(captor.capture());
            assertThat(captor.getValue().clientOrderId()).isEqualTo(CLOID);

            // OrderUpdateEvent는 발행되면 안 됨
            verify(eventPublisher, never()).publish(any(OrderUpdateDto.class));
        }

        @Test
        @DisplayName("예상치 못한 status가 오면 아무 이벤트도 발행하지 않고 경고 로그만 남긴다")
        void unexpectedStatus_publishesNothing() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            String responseJson = """
                {"status": "someUnknownStatus"}
                """;
            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(objectMapper.readTree(responseJson));

            poller.pollData().join();

            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("요청 포맷 검증")
    class RequestFormatValidation {
        @Test
        @DisplayName("올바른 POST 요청을 구성한다 - body에 type, user, oid 포함")
        void buildsCorrectRequest() {
            InFlightOrder inFlightOrder = createInFlightOrder(TRADING_PAIR, CLOID);
            when(orderTracker.getAll()).thenReturn(List.of(inFlightOrder));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("open", 1700000001000L));

            poller.pollData().join();

            ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
            verify(restAssistant).executeRequestAndGetJsonBody(captor.capture());

            RestRequest sent = captor.getValue();
            assertThat(sent.method()).isEqualTo(HttpMethod.POST);
            assertThat(sent.pathUrl()).isEqualTo(DerivativeApiSpec.INFO_PATH_URL);
            assertThat(sent.authRequired()).isFalse();  // info endpoint는 서명 불필요

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) sent.body();
            assertThat(body)
                    .containsEntry("type", "orderStatus")
                    .containsEntry("user", USER_ADDRESS)
                    .containsEntry("oid", CLOID);
        }
    }

    @Nested
    @DisplayName("다중 주문 처리")
    class MultipleOrderHandling {

        @Test
        @DisplayName("여러 주문을 각각 병렬 조회한다")
        void multipleOrders_eachPolledInParallel() {
            InFlightOrder order1 = createInFlightOrder("BTC-USDC", "0xaaa1111111111111111111111111111a");
            InFlightOrder order2 = createInFlightOrder("ETH-USDC", "0xbbb2222222222222222222222222222b");
            when(orderTracker.getAll()).thenReturn(List.of(order1, order2));

            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenReturn(buildOrderResponse("open", 1700000001000L));

            poller.pollData().join();
            // 각 주문마다 REST 호출
            verify(restAssistant, times(2)).executeRequestAndGetJsonBody(any());
            // 각 주문마다 이벤트 발행
            verify(eventPublisher, times(2)).publish(any(OrderUpdateDto.class));
        }

        @Test
        @DisplayName("일부 주문 polling 실패가 다른 주문에 영향을 주지 않는다")
        void oneOrderFails_doesNotAffectOthers() {
            InFlightOrder order1 = createInFlightOrder("BTC-USDC", "0xaaa1111111111111111111111111111a");
            InFlightOrder order2 = createInFlightOrder("ETH-USDC", "0xbbb2222222222222222222222222222b");
            when(orderTracker.getAll()).thenReturn(List.of(order1, order2));

            // 첫 번째 호출은 실패, 두 번째는 성공
            when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenThrow(new ExchangeApiException(HttpStatusCode.valueOf(500), "network error"))
                    .thenReturn(buildOrderResponse("filled", 1700000001000L));

            poller.pollData().join();

            // 성공한 주문의 이벤트는 발행됨
            verify(eventPublisher, times(1)).publish(any(OrderUpdateDto.class));
        }
    }

    private InFlightOrder createInFlightOrder(String tradingPair, String clientOrderId) {
        InFlightOrder order = mock(InFlightOrder.class);
        when(order.getTradingPair()).thenReturn(tradingPair);
        when(order.getClientOrderId()).thenReturn(clientOrderId);
        return order;
    }

    private JsonNode buildOrderResponse(String orderStatus, long statusTimestamp) {
        String json = """
            {
              "status": "order",
              "order": {
                "order": {
                  "coin": "BTC",
                  "side": "B",
                  "limitPx": "50000.0",
                  "sz": "0.1",
                  "oid": 987654321,
                  "timestamp": 1700000000000,
                  "origSz": "0.1",
                  "cloid": "%s"
                },
                "status": "%s",
                "statusTimestamp": %d
              }
            }
            """.formatted(CLOID, orderStatus, statusTimestamp);
        return objectMapper.readTree(json);
    }
}