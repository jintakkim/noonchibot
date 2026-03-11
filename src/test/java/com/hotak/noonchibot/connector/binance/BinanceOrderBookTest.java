package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.core.datatype.TradeType;
import com.hotak.noonchibot.core.orderbook.OrderBookEntry;
import com.hotak.noonchibot.core.orderbook.OrderBookMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class BinanceOrderBookTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TRADING_PAIR = "BTC-USDT";

    @Nested
    @DisplayName("스냅샷 메시지 파싱")
    class SnapshotMessageTest {

        @Test
        @DisplayName("정상적인 스냅샷 응답을 파싱한다")
        void parsesValidSnapshot() {
            JsonNode msg = createSnapshotMessage(
                    1000L,
                    1700000000L,
                    new String[][]{{"50000.0", "1.5"}, {"49999.0", "2.0"}},
                    new String[][]{{"50001.0", "0.5"}, {"50002.0", "1.0"}}
            );

            OrderBookMessage.SnapshotMessage result = BinanceOrderBook.snapshotMessageFromExchange(msg, TRADING_PAIR);

            assertThat(result.getTradingPair()).isEqualTo(TRADING_PAIR);
            assertThat(result.getUpdateId()).isEqualTo(1000L);
            assertThat(result.getTimestamp()).isEqualTo(Instant.ofEpochSecond(1700000000L));
            assertThat(result.getBids()).hasSize(2);
            assertThat(result.getAsks()).hasSize(2);
        }

        @Test
        @DisplayName("bid 엔트리의 가격과 수량이 올바르게 파싱된다")
        void parsesBidEntriesCorrectly() {
            JsonNode msg = createSnapshotMessage(
                    1000L,
                    1700000000L,
                    new String[][]{{"50000.12", "1.23456"}},
                    new String[][]{}
            );

            OrderBookMessage.SnapshotMessage result = BinanceOrderBook.snapshotMessageFromExchange(msg, TRADING_PAIR);

            OrderBookEntry bid = result.getBids().getFirst();
            assertThat(bid.price()).isEqualByComparingTo(new BigDecimal("50000.12"));
            assertThat(bid.amount()).isEqualByComparingTo(new BigDecimal("1.23456"));
        }

        @Test
        @DisplayName("ask 엔트리의 가격과 수량이 올바르게 파싱된다")
        void parsesAskEntriesCorrectly() {
            JsonNode msg = createSnapshotMessage(
                    1000L,
                    1700000000L,
                    new String[][]{},
                    new String[][]{{"50001.99", "0.00100"}}
            );

            OrderBookMessage.SnapshotMessage result = BinanceOrderBook.snapshotMessageFromExchange(msg, TRADING_PAIR);

            OrderBookEntry ask = result.getAsks().getFirst();
            assertThat(ask.price()).isEqualByComparingTo(new BigDecimal("50001.99"));
            assertThat(ask.amount()).isEqualByComparingTo(new BigDecimal("0.00100"));
        }

        @Test
        @DisplayName("빈 호가 목록을 정상적으로 처리한다")
        void handlesEmptyOrderBook() {
            JsonNode msg = createSnapshotMessage(1000L, 1700000000L, new String[][]{}, new String[][]{});
            OrderBookMessage.SnapshotMessage result = BinanceOrderBook.snapshotMessageFromExchange(msg, TRADING_PAIR);
            assertThat(result.getBids()).isEmpty();
            assertThat(result.getAsks()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Diff 메시지 파싱")
    class DiffMessageTest {

        @Test
        @DisplayName("정상적인 diff 이벤트를 파싱한다")
        void parsesValidDiff() {
            JsonNode msg = createDiffMessage(
                    100L,
                    200L,
                    1700000000000L,
                    new String[][]{{"49999.0", "3.0"}},
                    new String[][]{{"50001.0", "0.8"}}
            );

            OrderBookMessage.DiffMessage result = BinanceOrderBook.diffMessageFromExchange(msg, TRADING_PAIR);

            assertThat(result.getTradingPair()).isEqualTo(TRADING_PAIR);
            assertThat(result.getUpdateId()).isEqualTo(200L);
            assertThat(result.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
            assertThat(result.getBids()).hasSize(1);
            assertThat(result.getAsks()).hasSize(1);
        }

        @Test
        @DisplayName("수량이 0인 엔트리도 파싱한다 (삭제 이벤트)")
        void parsesZeroQuantityEntry() {
            JsonNode msg = createDiffMessage(
                    100L,
                    200L,
                    1700000000000L,
                    new String[][]{{"49999.0", "0"}},
                    new String[][]{}
            );

            OrderBookMessage.DiffMessage result = BinanceOrderBook.diffMessageFromExchange(msg, TRADING_PAIR);

            OrderBookEntry bid = result.getBids().getFirst();
            assertThat(bid.price()).isEqualByComparingTo(new BigDecimal("49999.0"));
            assertThat(bid.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("여러 개의 bid/ask 엔트리를 올바르게 파싱한다")
        void parsesMultipleEntries() {
            JsonNode msg = createDiffMessage(
                    100L,
                    200L,
                    1700000000000L,
                    new String[][]{{"49999.0", "1.0"}, {"49998.0", "2.0"}, {"49997.0", "3.0"}},
                    new String[][]{{"50001.0", "0.5"}, {"50002.0", "1.5"}}
            );

            OrderBookMessage.DiffMessage result = BinanceOrderBook.diffMessageFromExchange(msg, TRADING_PAIR);

            assertThat(result.getBids()).hasSize(3);
            assertThat(result.getAsks()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Trade 메시지 파싱")
    class TradeMessageTest {

        @Test
        @DisplayName("매도 체결(m=true → maker가 buyer → taker는 SELL)을 올바르게 파싱한다")
        void parsesSellTrade() {
            JsonNode msg = createTradeMessage(1700000000000L, 12345L, "50000.0", "0.5", true);

            OrderBookMessage.TradeMessage result = BinanceOrderBook.tradeMessageFromExchange(msg, TRADING_PAIR);

            assertThat(result.getTradingPair()).isEqualTo(TRADING_PAIR);
            assertThat(result.getTradeType()).isEqualTo(TradeType.SELL);
            assertThat(result.getTradeId()).isEqualTo(12345L);
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("50000.0"));
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("0.5"));
            assertThat(result.getTimestamp()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        }

        @Test
        @DisplayName("매수 체결(m=false → maker가 seller → taker는 BUY)을 올바르게 파싱한다")
        void parsesBuyTrade() {
            JsonNode msg = createTradeMessage(1700000000000L, 12346L, "50001.0", "1.0", false);
            OrderBookMessage.TradeMessage result = BinanceOrderBook.tradeMessageFromExchange(msg, TRADING_PAIR);
            assertThat(result.getTradeType()).isEqualTo(TradeType.BUY);
        }
    }

    // === 테스트 데이터 생성 헬퍼 ===

    private static JsonNode createSnapshotMessage(long lastUpdateId, long eventTime, String[][] bids, String[][] asks) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("lastUpdateId", lastUpdateId);
        msg.put("eventTime", eventTime);
        msg.set("bids", createEntries(bids));
        msg.set("asks", createEntries(asks));
        return msg;
    }

    private static JsonNode createDiffMessage(long firstUpdateId, long lastUpdateId, long eventTime, String[][] bids, String[][] asks) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("U", firstUpdateId);
        msg.put("u", lastUpdateId);
        msg.put("E", eventTime);
        msg.set("b", createEntries(bids));
        msg.set("a", createEntries(asks));
        return msg;
    }

    private static JsonNode createTradeMessage(long eventTime, long tradeId, String price, String qty, boolean isBuyerMaker) {
        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("E", eventTime);
        msg.put("t", tradeId);
        msg.put("p", price);
        msg.put("q", qty);
        msg.put("m", isBuyerMaker);
        return msg;
    }

    private static ArrayNode createEntries(String[][] entries) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        for (String[] entry : entries) {
            ArrayNode entryNode = objectMapper.createArrayNode();
            entryNode.add(entry[0]);
            entryNode.add(entry[1]);
            arrayNode.add(entryNode);
        }
        return arrayNode;
    }
}
