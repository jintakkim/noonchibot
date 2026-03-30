package com.hotak.noonchibot.connector.derivative.binance;

import com.hotak.noonchibot.connector.web.WsAssistant;
import com.hotak.noonchibot.connector.web.WsResponse;
import com.hotak.noonchibot.core.orderbook.AbstractInFlightOrderBookDataSourceTest;
import com.hotak.noonchibot.core.orderbook.AbstractOrderBookDataSource;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import tools.jackson.databind.JsonNode;

public class BinanceDerivativeInFlightOrderBookDataSourceTest extends AbstractInFlightOrderBookDataSourceTest {
    @Override
    protected AbstractOrderBookDataSource createDataSource(WsAssistant wsAssistant, AsyncTaskExecutor taskExecutor, TaskScheduler taskScheduler) {
        return null;
    }

    @Override
    protected WsResponse createAckResponse() {
        return null;
    }

    @Override
    protected JsonNode createErrorNode(String errorMsg) {
        return null;
    }

    @Override
    protected JsonNode createDiffMessageNode(String tradingPair) {
        return null;
    }

    @Override
    protected JsonNode createTradeMessageNode(String tradingPair) {
        return null;
    }
}
