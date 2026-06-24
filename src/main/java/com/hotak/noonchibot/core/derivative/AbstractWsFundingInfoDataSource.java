package com.hotak.noonchibot.core.derivative;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.config.Phases;
import com.hotak.noonchibot.core.event.EventPublisher;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import com.hotak.noonchibot.core.orderbook.FundingInfoMessage;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public abstract class AbstractWsFundingInfoDataSource extends AbstractWebsocketDataSource implements LifecycleAware {
    private final EventPublisher eventPublisher;
    final List<String> pairsToSubscribe;

    public AbstractWsFundingInfoDataSource(
            WsAssistant wsAssistant,
            ObjectMapper objectMapper,
            IoExecutor ioExecutor,
            EventPublisher eventPublisher,
            List<String> pairsToSubscribe
            ) {
        super(wsAssistant, objectMapper, ioExecutor);
        this.eventPublisher = eventPublisher;
        this.pairsToSubscribe = pairsToSubscribe;
    }

    @Override
    protected void onConnected() {
        sendSubscribe(new HashSet<>(pairsToSubscribe));
    }

    @Override
    protected void processMessage(WsResponse response) {
        if (response.messageType() != WsResponse.MessageType.TEXT) return;
        JsonNode msg = objectMapper.readTree(response.data());

        if (isErrorMessage(msg)) {
            throw new WebsocketErrorMessageReceivedException(msg.toString());
        }
        if (isAckMessage(msg)) return;

        FundingInfoMessage fundingMessage = parseFundingInfoMessage(msg);
        eventPublisher.publish(new FundingInfoEvent.Received(
                fundingMessage.tradingPair(),
                fundingMessage.eventTime(),
                fundingMessage.markPrice(),
                fundingMessage.fundingRate(),
                fundingMessage.nextFundingTime(),
                fundingMessage.fundingInterval()
        ));
    }

    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(Set<String> tradingPairs);
    protected abstract boolean isErrorMessage(JsonNode msg);
    protected abstract boolean isAckMessage(JsonNode msg);
    protected abstract FundingInfoMessage parseFundingInfoMessage(JsonNode msg);

    @Override
    public int phase() {
        return Phases.FUNDING_INFO_DATASOURCE_SETUP;
    }
}
