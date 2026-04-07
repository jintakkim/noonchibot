package com.hotak.noonchibot.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class StructuredInFlightOrderIdGeneratorTest {

    private final StructuredOrderIdGenerator generator = new StructuredOrderIdGenerator();

    @Test
    @DisplayName("매수 주문 ID는 B로 시작하고, 매도 주문 ID는 S로 시작한다")
    void sidePrefix() {
        String buyId = generator.createClientOrderId(true, "BTC-USDT", null, 0);
        String sellId = generator.createClientOrderId(false, "BTC-USDT", null, 0);

        assertThat(buyId).startsWith("B");
        assertThat(sellId).startsWith("S");
    }

    @Test
    @DisplayName("거래쌍의 첫 글자와 마지막 글자가 ID에 포함된다")
    void tradingPairAbbreviation() {
        String id = generator.createClientOrderId(true, "BTC-USDT", null, 0);

        // BTC -> BC, USDT -> UT
        assertThat(id).startsWith("BBCUT");
    }

    @Test
    @DisplayName("prefix가 지정되면 ID 맨 앞에 붙는다")
    void withPrefix() {
        String id = generator.createClientOrderId(true, "BTC-USDT", "HBOT-", 0);

        assertThat(id).startsWith("HBOT-BBCUT");
    }

    @Test
    @DisplayName("prefix가 null이면 side부터 시작한다")
    void withoutPrefix() {
        String id = generator.createClientOrderId(true, "ETH-USDT", null, 0);
        // ETH -> EH, USDT -> UT
        assertThat(id).startsWith("BEHUT");
    }

    @Test
    @DisplayName("연속 생성된 ID는 중복되지 않는다")
    void uniqueness() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(generator.createClientOrderId(true, "BTC-USDT", null, 0));
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("lengthLimit가 지정되면 ID 길이가 제한된다")
    void lengthLimit() {
        String id = generator.createClientOrderId(true, "BTC-USDT", null, 20);

        assertThat(id).hasSize(20);
    }

    @Test
    @DisplayName("$ 기호가 포함된 심볼은 제거된다")
    void dollarSignRemoved() {
        String id = generator.createClientOrderId(true, "$PEPE-USDT", null, 0);

        assertThat(id).doesNotContain("$");
    }

    @Test
    @DisplayName("nonce는 단조 증가한다")
    void monotonicNonce() {
        String id1 = generator.createClientOrderId(true, "BTC-USDT", null, 0);
        String id2 = generator.createClientOrderId(true, "BTC-USDT", null, 0);

        // 같은 prefix를 가지므로 suffix(nonce 부분)로 비교하면 id2가 더 크다
        assertThat(id2).isGreaterThan(id1);
    }
}
