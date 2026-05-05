package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.WsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DiffSupportingOrderBookDataSourceTest extends AbstractOrderBookDataSourceTest {
    public DiffSupportingOrderBookDataSourceTest(String quoteAsset) {
        super(quoteAsset);
    }

    protected abstract JsonNode createDiffMessageNode(String tradingPair);

    @Test
    @DisplayName("DIFF 메시지 수신 시 파싱되어 해당 페어의 스트림으로 정상 전달(캐스팅)된다")
    void processMessageCastsDiffToStream() throws InterruptedException {
        OrderBookMessageStream stream = dataSource.subscribeOrderBookStream(createTradingPair("BTC"));
        JsonNode diffNode = createDiffMessageNode(createTradingPair("BTC"));
        queue.add(new WsResponse(diffNode.toString(), WsResponse.MessageType.TEXT));
        dataSource.processMessage();
        OrderBookMessage message = stream.take();
        assertThat(message).isInstanceOf(OrderBookMessage.DiffMessage.class);
    }
}
