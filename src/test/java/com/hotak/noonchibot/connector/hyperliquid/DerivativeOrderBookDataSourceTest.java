package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSourceTest;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DerivativeOrderBookDataSourceTest extends AbstractOrderBookDataSourceTest {

    private RestAssistantImpl restAssistant;
    private TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private DerivativeOrderBookDataSource derivativeOrderBookDataSource;

    public DerivativeOrderBookDataSourceTest() {
        super("USDC");
    }

    @BeforeEach
    void setUp() {
        derivativeOrderBookDataSource = (DerivativeOrderBookDataSource) dataSource;
    }

    @Test
    @DisplayName("Hyperliquid l2Book REST 응답을 SnapshotMessage로 파싱한다")
    void getOrderBookSnapshot_parsesL2BookResponse() {
        // given
        String rawResponse = """
            {
              "coin": "BTC",
              "time": 1754450974231,
              "levels": [
                [
                  {"px": "113377.0", "sz": "7.6699", "n": 17},
                  {"px": "113376.0", "sz": "4.13714", "n": 8}
                ],
                [
                  {"px": "113397.0", "sz": "0.11543", "n": 3},
                  {"px": "113398.0", "sz": "2.5", "n": 5}
                ]
              ]
            }
            """;
        JsonNode responseNode = objectMapper.readTree(rawResponse);

        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(responseNode);

        // when
        OrderBookMessage.SnapshotMessage snapshot = derivativeOrderBookDataSource.getOrderBookSnapshot("BTC-USDC");

        // then
        assertThat(snapshot.getTradingPair()).isEqualTo("BTC-USDC");
        assertThat(snapshot.getUpdateId()).isEqualTo(1754450974231L);
        assertThat(snapshot.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1754450974231L));
        assertThat(snapshot.getUpdateId()).isEqualTo(1754450974231L);

        // bids 검증
        assertThat(snapshot.getBids()).hasSize(2);
        assertThat(snapshot.getBids().get(0).price()).isEqualByComparingTo("113377.0");
        assertThat(snapshot.getBids().get(0).amount()).isEqualByComparingTo("7.6699");
        assertThat(snapshot.getBids().get(1).price()).isEqualByComparingTo("113376.0");
        assertThat(snapshot.getBids().get(1).amount()).isEqualByComparingTo("4.13714");

        // asks 검증
        assertThat(snapshot.getAsks()).hasSize(2);
        assertThat(snapshot.getAsks().get(0).price()).isEqualByComparingTo("113397.0");
        assertThat(snapshot.getAsks().get(0).amount()).isEqualByComparingTo("0.11543");
        assertThat(snapshot.getAsks().get(1).price()).isEqualByComparingTo("113398.0");
        assertThat(snapshot.getAsks().get(1).amount()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("스냅샷 요청 시 올바른 RestRequest를 구성한다")
    void getOrderBookSnapshot_buildsCorrectRequest() {
        // given
        String rawResponse = """
            {
              "coin": "BTC",
              "time": 1754450974231,
              "levels": [[], []]
            }
            """;
        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(objectMapper.readTree(rawResponse));

        derivativeOrderBookDataSource.getOrderBookSnapshot("BTC-USDC");

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(restAssistant).executeRequestAndGetJsonBody(captor.capture());

        RestRequest sent = captor.getValue();
        assertThat(sent.method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.pathUrl()).isEqualTo(DerivativeApiSpec.INFO_PATH_URL);

        Map<String, Object> body = (Map<String, Object>) sent.body();
        assertThat(body).containsEntry("type", "l2Book");
        assertThat(body).containsEntry("coin", "BTC");
    }

    @Override
    protected AbstractOrderBookDataSource createDataSource(WsAssistantImpl wsAssistant, IoExecutor ioExecutor, TaskScheduler taskScheduler) {
        restAssistant = Mockito.mock(RestAssistantImpl.class);
        tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(Map.of("BTC-USDC", "BTC", "ETH-USDC", "ETH"));
        return new DerivativeOrderBookDataSource(
                wsAssistant, objectMapper, ioExecutor, taskScheduler, restAssistant, tradingPairSymbolRegistry
        );
    }


    @Override
    protected WsResponse createAckResponse() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("channel", "subscriptionResponse");
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("method", "subscribe");
        ObjectNode subscription = objectMapper.createObjectNode();
        subscription.put("type", "l2Book");
        subscription.put("coin", "BTC");
        dataNode.set("subscription", subscription);
        node.set("data", dataNode);
        return new WsResponse(node.toString(), WsResponse.MessageType.TEXT);
    }

    @Override
    protected JsonNode createErrorNode(String errorMsg) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("channel", "error");
        node.put("data", errorMsg);
        return node;
    }

    @Override
    protected JsonNode createTradeMessageNode(String tradingPair) {
        String coin = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("channel", "trades");

        ArrayNode dataArray = objectMapper.createArrayNode();  // trades는 배열
        ObjectNode trade = objectMapper.createObjectNode();
        trade.put("coin", coin);
        trade.put("side", "B");                // Bid = BUY
        trade.put("px", "113400.0");
        trade.put("sz", "0.05");
        trade.put("hash", "0x0000000000000000000000000000000000000000000000000000000000000001");
        trade.put("time", 1754450974231L);
        trade.put("tid", 12345678L);

        ArrayNode users = objectMapper.createArrayNode();
        users.add("0xbuyer0000000000000000000000000000000000");
        users.add("0xseller000000000000000000000000000000000");
        trade.set("users", users);

        dataArray.add(trade);
        root.set("data", dataArray);

        return root;
    }
}
