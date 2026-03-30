package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.AbstractWebsocketDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.AsyncTaskExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class AbstractFundingInfoDataSource extends AbstractWebsocketDataSource implements FundingInfoDataSource, SmartLifecycle {
    private final Map<String, Set<FundingInfoMessageStream>> fundingInfoMessageStreams = new ConcurrentHashMap<>();

    public AbstractFundingInfoDataSource(WsAssistant wsAssistant, String publicWsUrl, ObjectMapper objectMapper, AsyncTaskExecutor taskExecutor) {
        super(wsAssistant, publicWsUrl, objectMapper, taskExecutor);
    }

    @Override
    public FundingInfoMessageStream subscribe(String tradingPair) {
        Set<FundingInfoMessageStream> pairStreams = fundingInfoMessageStreams.computeIfAbsent(tradingPair, k -> ConcurrentHashMap.newKeySet());
        FundingInfoMessageStream stream = new FundingInfoMessageStream(tradingPair);
        pairStreams.add(stream);
        if (pairStreams.size() == 1) {
            sendSubscribe(tradingPair);
        }
        return stream;
    }

    @Override
    public void unsubscribe(FundingInfoMessageStream stream) {
        Set<FundingInfoMessageStream> streams = fundingInfoMessageStreams.get(stream.tradingPair);
        if(streams == null) return;
        streams.remove(stream);
        if (streams.isEmpty()) {
            fundingInfoMessageStreams.remove(stream.tradingPair);
            sendUnsubscribe(stream.tradingPair);
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
        Set<String> tradingPairs = fundingInfoMessageStreams.keySet();
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


    protected abstract void sendSubscribe(String tradingPair);
    protected abstract void sendSubscribe(Set<String> tradingPairs);
    protected abstract void sendUnsubscribe(String tradingPair);
    protected abstract boolean isErrorMessage(JsonNode msg);
    protected abstract boolean isAckMessage(JsonNode msg);
    protected abstract FundingInfoMessage parseFundingInfoMessage(JsonNode msg);


    private void castMessageToStream(FundingInfoMessage message) {
        Set<FundingInfoMessageStream> streams = fundingInfoMessageStreams.get(message.tradingPair());
        if(streams == null) {
            log.warn("no streams found for trading pair {}, possibly need to send unsubscribe message to exchange server", message.tradingPair());
            return;
        }
        streams.forEach(stream -> stream.add(message));
    }
}
