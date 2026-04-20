package com.hotak.noonchibot.connector.bybit;

import com.hotak.noonchibot.core.datatype.UserStreamEventParser;
import com.hotak.noonchibot.core.event.BalanceUpdateEvent;
import com.hotak.noonchibot.core.event.ExchangeEvent;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BybitBalanceUpdateParser implements UserStreamEventParser {
    @Override
    public boolean canParse(JsonNode msg) {
        return "wallet".equals(msg.path("topic").asString());
    }

    @Override
    public List<ExchangeEvent> parse(JsonNode msg) {
        Instant eventTime = Instant.ofEpochMilli(msg.get("creationTime").asLong());
        return streamCoins(msg)
                .map(coin -> {
                    // Bybit UNIFIED 에서 availableToWithdraw 는 담보 상태일 때 빈 문자열이라 신뢰 불가.
                    // walletBalance(보유량) - locked(주문에 묶인 양) 로 가용 잔고를 계산한다.
                    BigDecimal walletBalance = coin.path("walletBalance").asDecimal();
                    BigDecimal lockedAmount = coin.path("locked").asDecimal();
                    return (ExchangeEvent) new BalanceUpdateEvent(
                            coin.path("coin").asString(),
                            walletBalance,
                            walletBalance.subtract(lockedAmount),
                            eventTime
                    );
                })
                .toList();
    }

    private Stream<JsonNode> streamCoins(JsonNode msg) {
        return StreamSupport.stream(msg.path("data").spliterator(), false)
                .flatMap(account -> StreamSupport.stream(account.path("coin").spliterator(), false));
    }
}