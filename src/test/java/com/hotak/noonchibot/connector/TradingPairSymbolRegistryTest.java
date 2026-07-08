package com.hotak.noonchibot.connector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TradingPairSymbolRegistryTest {
    private SimpleTradingPairSymbolRegistry registry;

    @BeforeEach
    void setUp() {
        Map<String, String> symbolTradingPairMap = Map.of(
                "BTCUSDT", "BTC/USDT",
                "ETHUSDT", "ETH/USDT",
                "XRPUSDT", "XRP/USDT"
        );
        Map<String, String> tradingPairSymbolMap = Map.of(
                "BTC/USDT", "BTCUSDT",
                "ETH/USDT", "ETHUSDT",
                "XRP/USDT", "XRPUSDT"
        );
        registry = new SimpleTradingPairSymbolRegistry(symbolTradingPairMap, tradingPairSymbolMap);
    }

    @DisplayName("트레이딩페어로 거래소 심볼을 조회한다")
    @Test
    void convertTradingPairToSymbol() {
        assertThat(registry.convertTradingPairToExchangeSymbol("BTC/USDT")).isEqualTo("BTCUSDT");
        assertThat(registry.convertTradingPairToExchangeSymbol("ETH/USDT")).isEqualTo("ETHUSDT");
    }

    @DisplayName("거래소 심볼로 트레이딩페어를 조회한다")
    @Test
    void convertSymbolToTradingPair() {
        assertThat(registry.convertExchangeSymbolToTradingPair("BTCUSDT")).isEqualTo("BTC/USDT");
        assertThat(registry.convertExchangeSymbolToTradingPair("XRPUSDT")).isEqualTo("XRP/USDT");
    }

    @DisplayName("존재하지 않는 트레이딩페어를 조회하면 예외를 던진다")
    @Test
    void throwExceptionForUnknownTradingPair() {
        assertThatThrownBy(() -> registry.convertTradingPairToExchangeSymbol("DOGE/USDT"))
                .isInstanceOf(TradingPairSymbolNotRegisteredException.class);
    }

    @DisplayName("존재하지 않는 거래소 심볼을 조회하면 예외를 던진다")
    @Test
    void throwExceptionForUnknownExchangeSymbol() {
        assertThatThrownBy(() -> registry.convertExchangeSymbolToTradingPair("DOGEUSDT"))
                .isInstanceOf(TradingPairSymbolNotRegisteredException.class);
    }

    @DisplayName("데이터가 있으면 비어있지 않다")
    @Test
    void notEmptyWhenDataExists() {
        assertThat(registry.isEmpty()).isFalse();
    }

    @DisplayName("양쪽 맵이 모두 비어있으면 비어있다")
    @Test
    void emptyWhenBothMapsEmpty() {
        SimpleTradingPairSymbolRegistry emptyRegistry =
                new SimpleTradingPairSymbolRegistry(Map.of(), Map.of());
        assertThat(emptyRegistry.isEmpty()).isTrue();
    }

    @DisplayName("한쪽 맵만 비어있으면 비어있지 않다")
    @Test
    void notEmptyWhenOneMapHasData() {
        SimpleTradingPairSymbolRegistry partialRegistry =
                new SimpleTradingPairSymbolRegistry(Map.of("BTCUSDT", "BTC/USDT"), Map.of());
        assertThat(partialRegistry.isEmpty()).isFalse();
    }

    @DisplayName("모든 트레이딩페어를 조회한다")
    @Test
    void getAllTradingPairs() {
        List<String> allPairs = registry.getAllTradingPairs();
        assertThat(allPairs).containsExactlyInAnyOrder("BTC/USDT", "ETH/USDT", "XRP/USDT");
    }

    @DisplayName("조회된 트레이딩페어 리스트를 수정해도 원본에 영향이 없다")
    @Test
    void returnedListIsDefensiveCopy() {
        List<String> allPairs = registry.getAllTradingPairs();
        allPairs.clear();
        assertThat(registry.getAllTradingPairs()).hasSize(3);
    }
}
