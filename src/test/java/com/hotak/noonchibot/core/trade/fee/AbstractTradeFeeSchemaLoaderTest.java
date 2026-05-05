package com.hotak.noonchibot.core.trade.fee;

import com.hotak.noonchibot.connector.AbstractTradeFeeSchemaLoader;
import com.hotak.noonchibot.connector.TradingPairSymbolRegistry;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.VirtualThreadIoExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractTradeFeeSchemaLoaderTest {

    protected static final ObjectMapper objectMapper = new ObjectMapper();

    protected TradingPairSymbolRegistry tradingPairSymbolRegistry;
    protected IoExecutor ioExecutor;
    protected AbstractTradeFeeSchemaLoader loader;

    /**
     * 자식 테스트가 거래소별 fixture를 주입할 수 있게 hook 제공.
     */
    protected abstract AbstractTradeFeeSchemaLoader createLoader(
            IoExecutor ioExecutor,
            TradingPairSymbolRegistry tradingPairSymbolRegistry
    );

    /** 테스트가 사용할 watchlist 페어들 */
    protected abstract TradingPairSymbolRegistry createTradingPairSymbolRegistry();

    /** 거래소가 정상 응답했을 때 페어별 expected schema. */
    protected abstract Map<String, TradeFeeSchema> expectedSchemas();

    @BeforeEach
    void setUp() {
        tradingPairSymbolRegistry = createTradingPairSymbolRegistry();
        ioExecutor = new VirtualThreadIoExecutor();
        // 자식 테스트가 추가 mock 설정 필요 시 setUp() 후에 처리

        loader = createLoader(ioExecutor, tradingPairSymbolRegistry);
    }

    @Test
    @DisplayName("초기화 후 조회시 watchlist의 모든 페어에 대해 schema를 반환한다")
    void getReturnsSchemaForAllWatchlistPairs() {
        loader.start();
        for (String pair : tradingPairSymbolRegistry.getAllTradingPairs()) {
            TradeFeeSchema schema = loader.get(pair);
            assertThat(schema)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("초기화 호출 후 조회시 페어별 schema가 expected와 일치한다")
    void schemaMatchesExpected() {
        loader.start();
        Map<String, TradeFeeSchema> expected = expectedSchemas();
        for (var entry : expected.entrySet()) {
            TradeFeeSchema actual = loader.get(entry.getKey());
            assertSchemaEquals(actual, entry.getValue());
        }
    }

    @Test
    @DisplayName("watchlist에 없는 페어로 조회하면 예외가 발생한다")
    void getUnknownPairThrows() {
        loader.start();
        assertThatThrownBy(() -> loader.get("UNKNOWN-PAIR")).isInstanceOf(IllegalArgumentException.class);
    }

    private void assertSchemaEquals(TradeFeeSchema actual, TradeFeeSchema expected) {
        assertThat(actual.makerFeeRate()).isEqualByComparingTo(expected.makerFeeRate());
        assertThat(actual.takerFeeRate()).isEqualByComparingTo(expected.takerFeeRate());
        assertThat(actual.buyFeeDeductedFromReturns()).isEqualTo(expected.buyFeeDeductedFromReturns());
    }
}