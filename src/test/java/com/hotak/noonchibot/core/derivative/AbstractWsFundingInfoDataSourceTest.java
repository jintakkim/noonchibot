package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.connector.web.WebSocketErrorMessageReceivedException;
import com.hotak.noonchibot.connector.web.WsRequest;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.connector.web.testutils.MockWsAssistant;
import com.hotak.noonchibot.core.AbstractWebsocketDataSourceTest;
import com.hotak.noonchibot.core.event.TestEventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractWsFundingInfoDataSourceTest<T extends AbstractWsFundingInfoDataSource> extends AbstractWebsocketDataSourceTest<T> {
    private TestEventPublisher eventPublisher;

    protected abstract String markPriceStreamUri();

    protected abstract WsResponse fundingInfoMessage();

    /** normalFundingInfoMessage()가 처리됐을 때 발행돼야 할 이벤트 */
    protected abstract FundingInfoEvent.Received expectedReceivedEvent();

    /** 거래소별 ack 메시지 — 처리되어도 이벤트 발행 X -> ack이 없다면 empty리턴 */
    protected abstract Optional<WsResponse> ackMessage();

    /** 거래소별 error 메시지 — 처리되면 WebSocketSubscriptionFailedException 던짐 */
    protected abstract WsResponse errorMessage();

    /** 받은 요청들이 위 pair 목록 전체를 구독하는지 검증. */
    protected abstract void assertAllPairsSubscribed(List<WsRequest> requests);

    @Override
    protected T createWebsocketDataSource(MockWsAssistant wsAssistant) {
        eventPublisher = new TestEventPublisher();
        return createWsFundingInfoDataSource(wsAssistant, eventPublisher);
    }

    protected abstract T createWsFundingInfoDataSource(MockWsAssistant wsAssistant, TestEventPublisher eventPublisher);

    @Test
    @DisplayName("연결 후 모든 trading pair에 대해 markPrice 스트림을 구독한다")
    void onStart_subscribesAllPairsToMarkPriceStream() {
        runWith(markPriceStreamUri(), server -> {
            doConnection();
            List<WsRequest> requests = server.receivedRequests();
            assertAllPairsSubscribed(requests);
        });
    }

    @Test
    @DisplayName("markPrice 메시지 수신시 FundingInfoEvent.Received 이벤트 발행")
    void onMarkPriceMessage_publishesReceivedEvent() {
        dataSource.processMessage(fundingInfoMessage());
        var occurred = eventPublisher.getFirstEventOfType(FundingInfoEvent.Received.class);
        assertThat(occurred)
                .isPresent()
                .hasValue(expectedReceivedEvent());
    }

    @Test
    @DisplayName("ack 메시지는 무시되고 Received 이벤트 발행하지 않음")
    void onAckMessage_doesNotPublishEvent() {
        Optional<WsResponse> response = ackMessage();
        Assumptions.assumeTrue(response.isPresent(), "이 거래소는 ack 메시지를 사용하지 않음");
        dataSource.processMessage(response.get());
        assertThat(eventPublisher.hasEventOfType(FundingInfoEvent.Received.class)).isFalse();
    }

    @Test
    @DisplayName("error 메시지 수신시 예외를 발생시킨다.")
    void onErrorMessage_doesNotPublishReceivedEvent() {
        assertThatThrownBy(() -> dataSource.processMessage(errorMessage()))
                .isInstanceOf(WebSocketErrorMessageReceivedException.class);
        assertThat(eventPublisher.hasEventOfType(FundingInfoEvent.Received.class)).isFalse();
    }

}
