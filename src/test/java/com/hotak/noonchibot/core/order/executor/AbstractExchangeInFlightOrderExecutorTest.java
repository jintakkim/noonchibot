package com.hotak.noonchibot.core.order.executor;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.web.RestAssistant;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.TimeSynchronizer;
import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.datatype.TradingRule;
import com.hotak.noonchibot.core.event.ExchangeEventPublisher;
import com.hotak.noonchibot.core.event.OrderRequestSentEvent;
import com.hotak.noonchibot.core.event.TestExchangeEventPublisher;
import com.hotak.noonchibot.core.order.*;
import com.hotak.noonchibot.core.orderbook.OrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.TestOrderBookDataSource;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatusCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractExchangeInFlightOrderExecutorTest {
    public static final ObjectMapper objectMapper = new ObjectMapper();
    protected final String orderStatusPath;
    protected final String accountBalancePath;
    protected final String tradesPath;
    public ExchangeEventPublisher eventPublisher;

    public static final TradeFeeSchema DEFAULT_FEE_SCHEMA = new TradeFeeSchema(
            null,
            new BigDecimal("0.001"),  // maker 0.1%
            new BigDecimal("0.001"),  // taker 0.1%
            true,
            List.of(),
            List.of()
    );
    public static final List<String> TRADING_PAIRS = List.of("BTC-USDT", "ETH-USDT");
    public static final TradingRule BTC_USDT_RULE = new TradingRule(
            "BTC-USDT",
            new BigDecimal("0.00001"),
            new BigDecimal("9000"),
            new BigDecimal("0.01"),
            new BigDecimal("0.00001"),
            new BigDecimal("5"),
            8,
            Set.of(OrderType.LIMIT, OrderType.MARKET, OrderType.LIMIT_MAKER),
            "USDT",
            "BTC"
    );

    public static final TradingRule ETH_USDT_RULE = new TradingRule(
            "ETH-USDT",
            new BigDecimal("0.0001"),
            new BigDecimal("100000"),
            new BigDecimal("0.01"),
            new BigDecimal("0.0001"),
            new BigDecimal("5"),
            8,
            Set.of(OrderType.LIMIT, OrderType.MARKET, OrderType.LIMIT_MAKER),
            "USDT",
            "ETH"
    );

    public static final Map<String, TradingRule> TRADING_RULES = Map.of("BTC-USDT", BTC_USDT_RULE, "ETH-USDT", ETH_USDT_RULE);

    public AbstractExchangeInFlightOrderExecutorTest(String orderStatusPath, String accountBalancePath, String tradesPath) {
        this.orderStatusPath = orderStatusPath;
        this.accountBalancePath = accountBalancePath;
        this.tradesPath = tradesPath;
    }

    protected abstract String convertTradingPairToExchangeSymbol(String tradingPair);

    protected abstract JsonNode createOrderPlacementResponse(String exchangeOrderId);
    protected abstract JsonNode createCancelResponse(String exchangeOrderId);
    protected abstract Exception createOrderNotFoundException();

    protected abstract AbstractExchangeOrderExecutor createExchangeOrderExecutor(
            OrderIdGenerator orderIdGenerator,
            OrderTracker orderTracker,
            TradingRuleRegistry tradingRuleRegistry,
            RestAssistant restAssistant,
            TradingPairSymbolRegistry tradingPairSymbolRegistry,
            OrderBookDataSource orderBookDataSource,
            ExchangeEventPublisher exchangeEventPublisher,
            TimeSynchronizer timeSynchronizer
    );

    protected AbstractExchangeOrderExecutor exchangeConnector;
    protected TestOrderIdGenerator testOrderIdGenerator;
    protected TestOrderBookDataSource testOrderBookDataSource;
    protected OrderTracker mockOrderTracker;
    protected TradingRuleRegistry tradingRuleRegistry;
    protected TradingPairSymbolRegistry tradingPairSymbolRegistry;
    protected RestAssistant mockRestAssistant;
    protected TestExchangeEventPublisher testExchangeEventPublisher;
    protected TimeSynchronizer mockTimeSynchronizer;


    @BeforeEach
    void setUp() {
        testOrderIdGenerator = new TestOrderIdGenerator();
        mockOrderTracker = createMockOrderTracker();
        tradingRuleRegistry = createTradingRuleRegistry();
        tradingPairSymbolRegistry = createTradingPairSymbolRegistry(TRADING_PAIRS);
        testOrderBookDataSource = new TestOrderBookDataSource();
        mockRestAssistant = createMockRestAssistant();
        testExchangeEventPublisher = new TestExchangeEventPublisher();
        mockTimeSynchronizer = createMockTimeSynchronizer();
        exchangeConnector = createExchangeOrderExecutor(
                testOrderIdGenerator,
                mockOrderTracker,
                tradingRuleRegistry,
                mockRestAssistant,
                tradingPairSymbolRegistry,
                testOrderBookDataSource,
                testExchangeEventPublisher,
                mockTimeSynchronizer
        );
    }

    private OrderTracker createMockOrderTracker() {
        return Mockito.mock(OrderTracker.class);
    }

    private RestAssistant createMockRestAssistant() {
        return Mockito.mock(RestAssistant.class);
    }

    private TimeSynchronizer createMockTimeSynchronizer() {
        return Mockito.mock(TimeSynchronizer.class);
    }

    protected TradingPairSymbolRegistry createTradingPairSymbolRegistry(List<String> tradingPairs) {
        Map<String, String> tradingPairSymbolMap = tradingPairs.stream().collect(Collectors.toMap(
                Function.identity(),
                this::convertTradingPairToExchangeSymbol
        ));
        return new SimpleTradingPairSymbolRegistry(tradingPairSymbolMap);
    }

    protected TradingRuleRegistry createTradingRuleRegistry() {
        return TRADING_RULES::get;
    }

    @Nested
    @DisplayName("주문 테스트")
    class InFlightOrderTest {
        @Test
        @DisplayName("TradingRule이 없는 거래쌍이면 예외가 발생한다")
        void noTradingRule() {
            assertThatThrownBy(() ->
                    exchangeConnector.buy("XRP-USDT", new BigDecimal("1.0"), OrderType.LIMIT, new BigDecimal("3000.0"))
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("매수 주문 시 clientOrderId를 반환한다")
        void buyReturnsClientOrderId() {
            stubOrderPlacementResponse("12345");
            String clientOrderId = exchangeConnector.buy("BTC-USDT", new BigDecimal("0.01"), OrderType.LIMIT, new BigDecimal("50000"));
            assertThat(clientOrderId).startsWith("BUY-BTC-USDT");
        }

        @Test
        @DisplayName("매수 주문 시 주문 요청 이벤트가 발생한다.")
        void buyOrderGenerateEvent() {
            stubOrderPlacementResponse("12345");
            exchangeConnector.buy("BTC-USDT", new BigDecimal("0.01"), OrderType.LIMIT, new BigDecimal("50000"));
            List<OrderRequestSentEvent> events = testExchangeEventPublisher.getEventsOfType(OrderRequestSentEvent.class);
            InFlightOrder inFlightOrder = events.getFirst().inFlightOrder();
            assertThat(inFlightOrder.getTradingPair()).isEqualTo("BTC-USDT");
            assertThat(inFlightOrder.getTradeType()).isEqualTo(TradeType.BUY);
            assertThat(inFlightOrder.getOrderType()).isEqualTo(OrderType.LIMIT);
            assertThat(inFlightOrder.getAmount()).isEqualByComparingTo(new BigDecimal("0.01"));
            assertThat(inFlightOrder.getPrice()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("최소 주문 수량 미만이면 FAILED 상태의 OrderUpdateEvent가 발생한다.")
        void buyBelowMinOrderSizeGenerateEvent() {
            exchangeConnector.buy("BTC-USDT", new BigDecimal("0.0000001"), OrderType.LIMIT, new BigDecimal("50000"));

            List<OrderUpdateEvent> events = testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class);
            OrderUpdateEvent update = events.getFirst();
            assertThat(update.newState()).isEqualTo(InFlightOrder.State.FAILED);
            assertThat(update.orderFailure().errorType()).isEqualTo("BelowMinOrderSizeException");
            assertThat(update.tradingPair()).isEqualTo("BTC-USDT");
        }

        @Test
        @DisplayName("최소 주문 금액(notional) 미만이면 FAILED 상태의 OrderUpdateEvent가 발생한다.")
        void buyBelowMinNotionalGenerateEvent() {
            exchangeConnector.buy("BTC-USDT", new BigDecimal("0.0001"), OrderType.LIMIT, new BigDecimal("1"));

            List<OrderUpdateEvent> events = testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class);
            OrderUpdateEvent update = events.getFirst();
            assertThat(update.newState()).isEqualTo(InFlightOrder.State.FAILED);
            assertThat(update.orderFailure().errorType()).isEqualTo("BelowMinNotionalException");
        }

        @Test
        @DisplayName("지원하지 않는 OrderType이면 FAILED 상태의 OrderUpdateEvent가 발생한다.")
        void buyWithUnsupportedOrderTypeGenerateEvent() {
            exchangeConnector.buy("BTC-USDT", new BigDecimal("0.01"), OrderType.AMM_SWAP, new BigDecimal("50000"));

            List<OrderUpdateEvent> events = testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class);
            OrderUpdateEvent update = events.getFirst();
            assertThat(update.newState()).isEqualTo(InFlightOrder.State.FAILED);
            assertThat(update.orderFailure().errorType()).isEqualTo("UnsupportedOrderTypeException");
        }

        @Test
        @DisplayName("시장가 주문 시 lastTradedPrice로 notional을 계산한다")
        void marketOrderUsesLastTradedPrice() {
            stubOrderPlacementResponse("12345");
            testOrderBookDataSource.setLastTradedPrice("BTC-USDT", new BigDecimal("50000"));
            // minNotional=5, price=50000, amount=0.001 → notional=50 > 5 통과
            String clientOrderId = exchangeConnector.buy("BTC-USDT", new BigDecimal("0.001"), OrderType.MARKET, null);
            assertThat(clientOrderId).isNotBlank();
            verify(mockRestAssistant).executeRequestAndGetJsonBody(any(RestRequest.class));
        }

        @Test
        @DisplayName("거래소 API 호출 실패 시 FAILED 상태의 OrderUpdateEvent가 발생한다")
        void placeOrderApiFailureGenerateEvent() {
            when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenThrow(new ExchangeApiException(HttpStatusCode.valueOf(400), "insufficient balance"));

            exchangeConnector.buy("BTC-USDT", new BigDecimal("0.01"), OrderType.LIMIT, new BigDecimal("50000"));

            List<OrderUpdateEvent> events = testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class);
            OrderUpdateEvent update = events.getFirst();
            assertThat(update.newState()).isEqualTo(InFlightOrder.State.FAILED);
        }
    }

    @Nested
    @DisplayName("주문 취소 테스트")
    class CancelTest {
        @Test
        @DisplayName("정상 취소 시 취소 이벤트가 발생한다.")
        void cancelSuccessGenerateEvent() {
            stubFindActiveOrder();
            stubCancelResponse("12345");
            exchangeConnector.cancel("BTC-USDT", "BUY-BTC-USDT-1");

            List<OrderUpdateEvent> events = testExchangeEventPublisher.getEventsOfType(OrderUpdateEvent.class);
            OrderUpdateEvent update = events.getFirst();
            assertThat(update.newState()).isEqualTo(InFlightOrder.State.CANCELED);
            assertThat(update.clientOrderId()).isEqualTo("BUY-BTC-USDT-1");
        }

        @Test
        @DisplayName("추적 중이지 않은 주문을 취소해도 예외가 발생하지 않는다")
        void cancelUntrackedOrder() {
            stubCancelResponse("12345");
            assertThatNoException().isThrownBy(() ->
                    exchangeConnector.cancel("BTC-USDT", "non-existent-inFlightOrder")
            );
        }

        @Test
        @DisplayName("거래소에서 주문을 찾을 수 없으면 processOrderNotFound가 호출된다")
        void cancelOrderNotFoundOnExchange() {
            stubFindActiveOrder();
            stubCancelOrderNotFound();
            exchangeConnector.cancel("BTC-USDT", "BUY-BTC-USDT-1");
            verify(mockOrderTracker).processOrderNotFound("BUY-BTC-USDT-1");
        }

        @Test
        @DisplayName("거래소 API 호출 실패 시 예외가 로깅되고 전파되지 않는다")
        void cancelApiFailure() {
            stubFindActiveOrder();
            when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                    .thenThrow(new ExchangeApiException(HttpStatusCode.valueOf(500), "internal error"));

            assertThatNoException().isThrownBy(() -> exchangeConnector.cancel("BTC-USDT", "BUY-BTC-USDT-1"));
        }
    }

    private void stubFindActiveOrder() {
        InFlightOrder trackedInFlightOrder = new InFlightOrder(
                "BUY-BTC-USDT-1", "BTC-USDT", OrderType.LIMIT,
                TradeType.BUY, new BigDecimal("0.01"), new BigDecimal("50000"), Instant.now()
        );
        when(mockOrderTracker.findActiveOrder("BUY-BTC-USDT-1", null)).thenReturn(Optional.of(trackedInFlightOrder));
    }

    protected void stubOrderPlacementResponse(String exchangeOrderId) {
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(createOrderPlacementResponse(exchangeOrderId));
    }

    protected void stubCancelResponse(String exchangeOrderId) {
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(createCancelResponse(exchangeOrderId));
    }

    protected void stubCancelOrderNotFound() {
        when(mockRestAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenThrow(createOrderNotFoundException());
    }
}
