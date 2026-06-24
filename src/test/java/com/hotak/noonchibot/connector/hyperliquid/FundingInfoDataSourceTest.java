package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.WsAssistantImpl;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FundingInfoDataSourceTest extends AbstractWsFundingInfoDataSourceTest<HyperliquidWsFundingInfoDataSource> {

    private TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private RestAssistantImpl restAssistant;


    protected FundingInfoDataSourceTest() {
        super("USDC");
    }

    @Override
    protected HyperliquidWsFundingInfoDataSource createDataSource(WsAssistantImpl wsAssistant, IoExecutor ioExecutor) {
        tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
                Map.of("BTC-USDC", "BTC", "ETH-USDC", "ETH")
        );
        restAssistant = Mockito.mock(RestAssistantImpl.class);

        return new HyperliquidWsFundingInfoDataSource(
                wsAssistant,
                DerivativeApiSpec.WS_URL,
                objectMapper,
                ioExecutor,
                tradingPairSymbolRegistry,
                restAssistant
        );
    }


    @Override
    protected JsonNode createFundingInfoMessage(
            String tradingPair, String markPrice, String fundingRate, long nextFundingTime) {
        String coin = tradingPair.split("-")[0];

        ObjectNode root = objectMapper.createObjectNode();
        root.put("channel", "activeAssetCtx");

        ObjectNode data = root.putObject("data");
        data.put("coin", coin);

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

    @Override
    protected WsResponse createAckResponse() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("channel", "subscriptionResponse");
        ObjectNode data = root.putObject("data");
        data.put("method", "subscribe");
        ObjectNode subscription = data.putObject("subscription");
        subscription.put("type", "activeAssetCtx");
        subscription.put("coin", "BTC");

        return new WsResponse(root.toString(), WsResponse.MessageType.TEXT);
    }

    @Override
    protected WsResponse createErrorResponse(String errorMsg) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("channel", "error");
        root.put("data", errorMsg);
        return new WsResponse(root.toString(), WsResponse.MessageType.TEXT);
    }


    @Test
    @DisplayName("Hyperliquid는 펀딩 인터벌이 1시간으로 고정된다")
    void fundingIntervalIsFixedToOneHour() {
        JsonNode msg = createFundingInfoMessage("BTC-USDT", "50000", "0.0001", 0L);

        FundingInfoMessage result = fundingInfoDataSource.parseFundingInfoMessage(msg);

        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("nextFundingTime은 가장 가까운 다음 정시(UTC)로 계산된다")
    void nextFundingTimeIsNextHourUtc() {
        JsonNode msg = createFundingInfoMessage("BTC-USDT", "50000", "0.0001", 0L);

        Instant before = Instant.now().truncatedTo(ChronoUnit.HOURS);
        FundingInfoMessage result = fundingInfoDataSource.parseFundingInfoMessage(msg);
        Instant after = Instant.now().truncatedTo(ChronoUnit.HOURS);

        // before+1h 또는 after+1h 이어야 한다 (밀리초 단위 호출 시간 차이 허용)
        Instant expectedFromBefore = before.plus(Duration.ofHours(1));
        Instant expectedFromAfter = after.plus(Duration.ofHours(1));

        assertThat(result.nextFundingTime())
                .isIn(expectedFromBefore, expectedFromAfter);
    }

    @Test
    @DisplayName("REST getFundingInfo 호출 시 activeAssetCtx endpoint를 호출한다")
    void getFundingInfoCallsActiveAssetCtxEndpoint() {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("coin", "BTC");
        ObjectNode ctx = response.putObject("ctx");
        ctx.put("funding", "0.0000125");
        ctx.put("markPx", "67234.0");
        ctx.put("midPx", "67233.5");
        ctx.put("oraclePx", "67220.0");

        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(response);

        FundingInfoMessage result = fundingInfoDataSource.getFundingInfo("BTC-USDC");

        assertThat(result.tradingPair()).isEqualTo("BTC-USDC");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("67234.0"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.0000125"));
        assertThat(result.fundingInterval()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("WS push 메시지의 ctx에 funding/markPx만 있어도 정상 파싱된다")
    void parsesMinimalCtxFields() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("channel", "activeAssetCtx");
        ObjectNode data = root.putObject("data");
        data.put("coin", "BTC");
        ObjectNode ctx = data.putObject("ctx");
        ctx.put("funding", "0.00005");
        ctx.put("markPx", "67000.0");

        FundingInfoMessage result = fundingInfoDataSource.parseFundingInfoMessage(root);

        assertThat(result.tradingPair()).isEqualTo("BTC-USDC");
        assertThat(result.markPrice()).isEqualByComparingTo(new BigDecimal("67000.0"));
        assertThat(result.fundingRate()).isEqualByComparingTo(new BigDecimal("0.00005"));
    }


    @Override
    protected void verifyNextFundingTime(FundingInfoMessage result, long expectedNextFundingTime) {
        Instant expected = Instant.now().truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));
        assertThat(result.nextFundingTime())
                .isBetween(expected.minus(Duration.ofHours(1)), expected.plus(Duration.ofHours(1)));
    }
}