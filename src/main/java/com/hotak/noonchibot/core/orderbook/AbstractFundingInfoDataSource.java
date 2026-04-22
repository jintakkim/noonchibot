package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import com.hotak.noonchibot.core.IoExecutor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public abstract class AbstractFundingInfoDataSource extends AbstractWebsocketDataSource implements FundingInfoDataSource {
    private final Map<String, Set<FundingInfoMessageStream>> streamsByPair = new HashMap<>();

    public AbstractFundingInfoDataSource(WsAssistant wsAssistant, String publicWsUrl, ObjectMapper objectMapper, IoExecutor ioExecutor) {
        super(wsAssistant, publicWsUrl, objectMapper, ioExecutor);
    }

    @Override
    public FundingInfoMessageStream subscribe(String tradingPair) {
        return batchSubscribe(Set.of(tradingPair));
    }

    @Override
    public FundingInfoMessageStream batchSubscribe(Set<String> tradingPairs) {
        FundingInfoMessageStream stream = new FundingInfoMessageStream(tradingPairs);

        Set<String> pairsToRequest = new HashSet<>();
        for (String pair : tradingPairs) {
            Set<FundingInfoMessageStream> streams = streamsByPair.computeIfAbsent(pair, k -> new HashSet<>());
            streams.add(stream);
            if (streams.size() == 1) {
                pairsToRequest.add(pair);
            }
        }
        if (!pairsToRequest.isEmpty()) {
            sendSubscribe(pairsToRequest);
        }
        return stream;
    }

    @Override
    public void unsubscribe(FundingInfoMessageStream stream) {
        Set<String> pairsToRelease = new HashSet<>();

        for (String pair : stream.getSubscribedTradingPairs()) {
            Set<FundingInfoMessageStream> streams = streamsByPair.get(pair);
            if (streams == null) continue;
            streams.remove(stream);
            if (streams.isEmpty()) {
                streamsByPair.remove(pair);
                pairsToRelease.add(pair);
            }
        }
        if (!pairsToRelease.isEmpty()) {
            sendUnsubscribe(pairsToRelease);
        }
    }

    @Override
    protected void onConnected() {
        resubscribeIfStreamExist();
    }

    /**
     * 끊김으로 인한 재연결 상황 등 일때 기존 구독분을 재구독한다.
     */
    private void resubscribeIfStreamExist() {
        Set<String> tradingPairs = streamsByPair.keySet();
        if(tradingPairs.isEmpty()) return;
        sendSubscribe(tradingPairs);
    }

    @Override
    protected void processMessage() throws InterruptedException {
        WsResponse response = wsConnection.take();
        if (response.messageType() != WsResponse.MessageType.TEXT) return;
        JsonNode msg = objectMapper.readTree(response.data());

        if (isErrorMessage(msg)) {
            throw new WebsocketSubscriptionFailedException(msg.toString());
        }
        if (isAckMessage(msg)) return;

        FundingInfoMessage fundingMessage = parseFundingInfoMessage(msg);
        castMessageToStream(fundingMessage);
    }

    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(Set<String> tradingPairs);
    protected abstract boolean isErrorMessage(JsonNode msg);
    protected abstract boolean isAckMessage(JsonNode msg);
    protected abstract FundingInfoMessage parseFundingInfoMessage(JsonNode msg);


    private void castMessageToStream(FundingInfoMessage message) {
        Set<FundingInfoMessageStream> streams = streamsByPair.get(message.tradingPair());
        if(streams == null) {
            log.warn("no streams found for trading pair {}, possibly need to send unsubscribe message to exchange server", message.tradingPair());
            return;
        }
        streams.forEach(stream -> stream.add(message));
    }
}
