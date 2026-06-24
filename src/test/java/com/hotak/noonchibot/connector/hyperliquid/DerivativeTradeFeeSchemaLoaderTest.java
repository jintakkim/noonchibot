package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.SimpleTradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.connector.web.RestAssistantImpl;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.trade.fee.AbstractTradeFeeSchemaLoaderTest;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DerivativeTradeFeeSchemaLoaderTest extends AbstractTradeFeeSchemaLoaderTest {
    private static final String USER_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    private static final BigDecimal MAKER_RATE = new BigDecimal("0.00015");
    private static final BigDecimal TAKER_RATE = new BigDecimal("0.00045");

    private RestAssistantImpl restAssistant;

    @BeforeEach
    void setup() {
        // 부모 setUp 이후 추가 mock 동작 설정
        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
                .thenReturn(buildUserFeesResponse(MAKER_RATE, TAKER_RATE));
    }

    @Override
    protected AbstractTradeFeeSchemaLoader createLoader(IoExecutor ioExecutor, TradingPairSymbolRegistry tradingPairSymbolRegistry) {
        if (restAssistant == null) {
            restAssistant = Mockito.mock(RestAssistantImpl.class);
        }
        return new DerivativeTradeFeeSchemaLoader(
                ioExecutor,
                tradingPairSymbolRegistry,
                restAssistant,
                USER_ADDRESS
        );
    }

    @Override
    protected TradingPairSymbolRegistry createTradingPairSymbolRegistry() {
        return new SimpleTradingPairSymbolRegistry(
                Map.of("BTC-USDC", "BTC", "ETH-USDC", "ETH", "SOL-USDC", "SOL")
        );
    }

    @Override
    protected Map<String, TradeFeeSchema> expectedSchemas() {
        TradeFeeSchema shared = new TradeFeeSchema(
                null, MAKER_RATE, TAKER_RATE, false, List.of(), List.of()
        );
        return Map.of(
                "BTC-USDC", shared,
                "ETH-USDC", shared,
                "SOL-USDC", shared
        );
    }

    @Test
    @DisplayName("Hyperliquid는 페어 수와 무관하게 userFees를 1번만 호출한다 (bulk fetch)")
    void callsUserFeesOnceRegardlessOfPairCount() {
        loader.start();

        verify(restAssistant, times(1))
                .executeRequestAndGetJsonBody(any(RestRequest.class));
    }

    @Test
    @DisplayName("userFees 요청은 user 주소를 body에 포함한다")
    void requestIncludesUserAddress() {
        loader.start();

        ArgumentCaptor<RestRequest> captor = ArgumentCaptor.forClass(RestRequest.class);
        verify(restAssistant).executeRequestAndGetJsonBody(captor.capture());

        RestRequest sent = captor.getValue();
        assertThat(sent.body())
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("user", USER_ADDRESS)
                .containsEntry("type", "userFees");
    }

    @Test
    @DisplayName("userFees 응답의 userAddRate가 maker rate, userCrossRate가 taker rate로 매핑된다")
    void mapsUserAddRateToMakerAndUserCrossRateToTaker() {
        loader.start();

        TradeFeeSchema schema = loader.get("BTC-USDC");
        assertThat(schema.makerFeeRate()).isEqualByComparingTo(MAKER_RATE);
        assertThat(schema.takerFeeRate()).isEqualByComparingTo(TAKER_RATE);
    }

//    @Test
//    @DisplayName("VIP tier 진입 등으로 응답 rate가 달라지면 새 schema가 반영된다")
//    void newRatesAreReflectedOnRestart() {
//        loader.start();
//        TradeFeeSchema initial = loader.get("BTC-USDC");
//        assertThat(initial.takerFeeRate()).isEqualByComparingTo("0.00045");
//
//        // VIP tier 1 진입 시뮬레이션
//        BigDecimal vipMakerRate = new BigDecimal("0.00012");
//        BigDecimal vipTakerRate = new BigDecimal("0.0004");
//        when(restAssistant.executeRequestAndGetJsonBody(any(RestRequest.class)))
//                .thenReturn(buildUserFeesResponse(vipMakerRate, vipTakerRate));
//
//        AbstractTradeFeeSchemaLoader newLoader = createLoader(ioExecutor, tradingPairSymbolRegistry);
//        newLoader.start();
//
//        TradeFeeSchema updated = newLoader.get("BTC-USDC");
//        assertThat(updated.makerFeeRate()).isEqualByComparingTo(vipMakerRate);
//        assertThat(updated.takerFeeRate()).isEqualByComparingTo(vipTakerRate);
//    }

    private JsonNode buildUserFeesResponse(BigDecimal makerRate, BigDecimal takerRate) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("userAddRate", makerRate.toPlainString());
        root.put("userCrossRate", takerRate.toPlainString());
        root.put("userSpotAddRate", "0.0004");
        root.put("userSpotCrossRate", "0.0007");
        root.put("activeReferralDiscount", "0.0");
        // 실제 응답엔 더 많은 필드가 있지만 코드가 사용하는 필드만 채움
        return root;
    }
}
