package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.core.derivative.AbstractWsFundingInfoDataSourceTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FundingInfoDataSourceTest extends AbstractWsFundingInfoDataSourceTest<HyperliquidWsFundingInfoDataSource> {
    private static final ObjectMapper OM = new ObjectMapper();

    private TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private RestAssistantImpl restAssistant;

    @Override
    protected String markPriceStreamUri() {
        return DerivativeApiSpec.WS_URL;
    }

    @Override
    protected HyperliquidWsFundingInfoDataSource createWsFundingInfoDataSource(
            MockWsAssistant wsAssistant,
            TestEventPublisher eventPublisher
    ) {
        tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
                Map.of("BTC-USDC", "BTC", "ETH-USDC", "ETH")
        );
        restAssistant = Mockito.mock(RestAssistantImpl.class);

        return new HyperliquidWsFundingInfoDataSource(
                wsAssistant,
                OM,
                new com.hotak.noonchibot.core.TestTaskScheduler(),
                event -> { },
                tradingPairSymbolRegistry,
                restAssistant,
                eventPublisher,
                tradingPairSymbolRegistry.getAllTradingPairs(),
                DerivativeApiSpec.WS_URL
        );
    }

    @Override
    protected WsResponse fundingInfoMessage() {
        return new WsResponse(
                createFundingInfoMessage("BTC-USDC", "50000", "0.0001", 0L).toString(),
                WsResponse.MessageType.TEXT
        );
    }

    @Override
    protected FundingInfoEvent.Received expectedReceivedEvent() {
        FundingInfoMessage message = dataSource.parseFundingInfoMessage(
                createFundingInfoMessage("BTC-USDC", "50000", "0.0001", 0L)
        );
        return new FundingInfoEvent.Received(
                "BTC-USDC",
                message.eventTime(),
                new BigDecimal("50000"),
                new BigDecimal("0.0001"),
                message.nextFundingTime(),
                Duration.ofHours(1)
        );
    }

    @Override
    protected Optional<WsResponse> ackMessage() {
        return Optional.of(createAckResponse());
    }

    @Override
    protected WsResponse errorMessage() {
        return createErrorResponse("bad subscription");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertAllPairsSubscribed(List<com.hotak.noonchibot.connector.web.WsRequest> requests) {
        assertThat(requests).hasSize(2);
        assertThat(requests.stream()
                .map(request -> (Map<String, Object>) request.payload())
                .map(payload -> (Map<String, Object>) payload.get("subscription"))
                .map(subscription -> (String) subscription.get("type")))
                .containsOnly("activeAssetCtx");
    }


    protected JsonNode createFundingInfoMessage(
            String tradingPair, String markPrice, String fundingRate, long nextFundingTime) {
        String coin = tradingPair.split("-")[0];

        ObjectNode root = OM.createObjectNode();
        root.put("channel", "activeAssetCtx");

        ObjectNode data = root.putObject("data");
        data.put("coin", coin);
        data.put("time", 1780000000000L);

        ObjectNode ctx = data.putObject("ctx");
        ctx.put("funding", fundingRate);
        ctx.put("markPx", markPrice);
        ctx.put("midPx", markPrice);
        ctx.put("oraclePx", markPrice);
        ctx.put("openInterest", "100.0");
        ctx.put("premium", "0.0");
        ctx.put("dayNtlVlm", "1000000.0");
        ctx.put("prevDayPx", markPrice);
        ArrayNode impactPxs = ctx.putArray("impactPxs");
        impactPxs.add(markPrice);
        impactPxs.add(markPrice);

        return root;
    }

    protected WsResponse createAckResponse() {
        ObjectNode root = OM.createObjectNode();
        root.put("channel", "subscriptionResponse");
        ObjectNode data = root.putObject("data");
        data.put("method", "subscribe");
        ObjectNode subscription = data.putObject("subscription");
        subscription.put("type", "activeAssetCtx");
        subscription.put("coin", "BTC");

        return new WsResponse(root.toString(), WsResponse.MessageType.TEXT);
    }

    protected WsResponse createErrorResponse(String errorMsg) {
        ObjectNode root = OM.createObjectNode();
        root.put("channel", "error");
        root.put("data", errorMsg);
        return new WsResponse(root.toString(), WsResponse.MessageType.TEXT);
    }


    @Test
    @DisplayName("Hyperliquid는 펀딩 인터벌이 1시간으로 고정된다")
    void fundingIntervalIsFixedToOneHour() {
        JsonNode msg = createFundingInfoMessage("BTC-USDT", "50000", "0.0001", 0L);

        FundingInfoMessage result = dataSource.parseFundingInfoMessage(msg);

        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("nextFundingTime은 가장 가까운 다음 정시(UTC)로 계산된다")
    void nextFundingTimeIsNextHourUtc() {
        JsonNode msg = createFundingInfoMessage("BTC-USDT", "50000", "0.0001", 0L);

        FundingInfoMessage result = dataSource.parseFundingInfoMessage(msg);
        Instant expected = result.eventTime()
                .truncatedTo(ChronoUnit.HOURS)
                .plus(Duration.ofHours(1));

        assertThat(result.nextFundingTime())
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("REST getFundingInfo 호출 시 activeAssetCtx endpoint를 호출한다")
    void getFundingInfoCallsActiveAssetCtxEndpoint() {
        ObjectNode response = OM.createObjectNode();
        response.put("coin", "BTC");
        ObjectNode ctx = response.putObject("ctx");
        ctx.put("funding", "0.0000125");
        ctx.put("markPx", "67234.0");
        ctx.put("midPx", "67233.5");
        ctx.put("oraclePx", "67220.0");

        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage result = dataSource.getFundingInfo("BTC-USDC");

        assertThat(result.tradingPair()).isEqualTo("BTC-USDC");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("67234.0"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0000125"));
        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("WS push 메시지의 ctx에 funding/markPx만 있어도 정상 파싱된다")
    void parsesMinimalCtxFields() {
        ObjectNode root = OM.createObjectNode();
        root.put("channel", "activeAssetCtx");
        ObjectNode data = root.putObject("data");
        data.put("coin", "BTC");
        ObjectNode ctx = data.putObject("ctx");
        ctx.put("funding", "0.00005");
        ctx.put("markPx", "67000.0");

        FundingInfoMessage result = dataSource.parseFundingInfoMessage(root);

        assertThat(result.tradingPair()).isEqualTo("BTC-USDC");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("67000.0"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.00005"));
    }


    protected void verifyNextFundingTime(FundingInfoMessage result, long expectedNextFundingTime) {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
        assertThat(result.nextFundingTime())
                .isBetween(expected.minus(Duration.ofHours(1)), expected.plus(Duration.ofHours(1)));
    }
}
