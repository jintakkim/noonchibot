package com.hotak.noonchibot.connector.hyperliquid;

import com.hotak.noonchibot.connector.*;
import com.hotak.noonchibot.connector.throttle.AsyncThrottler;
import com.hotak.noonchibot.connector.throttle.AsyncThrottlerImpl;
import com.hotak.noonchibot.connector.transfer.TransferDispatcher;
import com.hotak.noonchibot.connector.web.*;
import com.hotak.noonchibot.core.BootStrap;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.balance.AccountBalanceTracker;
import com.hotak.noonchibot.core.config.BotConstants;
import com.hotak.noonchibot.core.derivative.DerivativeInfoTracker;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.FundingPaymentRepository;
import com.hotak.noonchibot.core.derivative.FundingPaymentSnapshotUpdater;
import com.hotak.noonchibot.core.derivative.FundingPaymentTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;

public class DerivativeExchangeAdapterFactory {
    public static DerivativeExchangeConnector create(
            BootStrap bootStrap,
            HyperliquidConfig.Properties props,
            OrderSnapshotRepository orderSnapshotRepository,
            IoExecutor ioExecutor,
            TaskScheduler taskScheduler,
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            OrderSnapshotRepository orderHistoryRepository,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        EventBus eventBus = new EventBus();
        RestClient restClient = RestClient.builder().baseUrl(DerivativeApiSpec.BASE_URL).build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
                props.derivative().tradingPairSymbolMap()
        );
        AsyncThrottler throttler = new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        HyperliquidAuthenticator authenticator = new HyperliquidAuthenticator(
                objectMapper,
                messagePackMapper,
                null,
                true,
                props.address(),
                props.secret()
        );
        RestAssistant rawRestAssistant = new RestAssistantImpl(restClient, List.of(), List.of(), authenticator, throttler, objectMapper);
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderBook(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                tradingPairSymbolRegistry,
                eventBus,
                eventBus
        );
        bootStrap.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, tradeRepository, orderHistoryRepository, eventBus);
        bootStrap.register(orderTracker);

        OrderBookTracker orderBookTracker = new OrderBookTracker(
                eventBus,
                eventBus,
                true,
                new HashSet<>(tradingPairSymbolRegistry.getAllTradingPairs())
        );
        bootStrap.register(orderBookTracker);

        AccountBalanceTracker accountBalanceTracker = new AccountBalanceTracker(eventBus);
        bootStrap.register(accountBalanceTracker);

        DerivativeTradeFeeSchemaLoader feeSchemaLoader = new DerivativeTradeFeeSchemaLoader(
                ioExecutor,
                tradingPairSymbolRegistry,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradeFee(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                props.address()
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                props.address(),
                tradingPairSymbolRegistry,
                eventBus
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balanceDataSource = new RestBalanceDataSource(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                taskScheduler,
                props.address()
        );
        bootStrap.register(balanceDataSource);

        HLTradingRuleRegistry tradingRuleRegistry = new HLTradingRuleRegistry(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradingRules(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                tradingPairSymbolRegistry,
                taskScheduler
        );
        bootStrap.register(tradingRuleRegistry);

        OrderClientImpl orderClient = new OrderClientImpl(
                orderEntryRest(rawRestAssistant, circuitBreakerRegistry),
                orderCancelRest(rawRestAssistant, circuitBreakerRegistry),
                tradingRuleRegistry
        );
        ExchangeOrderExecutor exchangeOrderExecutor = new ExchangeOrderExecutor(
                new HyperliquidCloidGenerator(),
                orderTracker,
                tradingRuleRegistry,
                BotConstants.ORDER_ID_PREFIX,
                DerivativeApiSpec.MAX_ORDER_ID_LENGTH,
                DerivativeApiSpec.SUPPORTED_TIME_IN_FORCE,
                orderBookTracker,
                eventBus,
                orderClient,
                eventBus,
                Exchange.HYPERLIQUID_DERIVATIVE,
                orderSnapshotRepository
        );
        bootStrap.register(exchangeOrderExecutor);

        bootStrap.register(new OrderStatusDataSource(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderStatus(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                eventBus,
                props.address()
        ));
        bootStrap.register(new OrderStatusPoller(eventBus, orderTracker, taskScheduler));
        bootStrap.register(new TradeDataSource(
                tradingPairSymbolRegistry,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.trades(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                eventBus,
                props.address()
        ));
        bootStrap.register(new TradePoller(eventBus, taskScheduler, orderTracker));
        DeriviativeInnerTransfer innerTransfer = new DeriviativeInnerTransfer(
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.transfer(Exchange.HYPERLIQUID_DERIVATIVE), 1)
        );
        bootStrap.register(new TransferDispatcher(List.of(innerTransfer), eventBus, eventBus));

        HyperliquidWsFundingInfoDataSource fundingInfoDataSource = new HyperliquidWsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                tradingPairSymbolRegistry,
                rest(rawRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.funding(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                tradingPairSymbolRegistry.getAllTradingPairs()
        );
        bootStrap.register(fundingInfoDataSource);

        FundingInfoTracker fundingInfoTracker = new FundingInfoTracker(
                Duration.ofHours(1),
                "USDC",
                tradingPairSymbolRegistry,
                eventBus
        );
        bootStrap.register(fundingInfoTracker);

        DerivativeInfoTracker derivativeInfoTracker = new DerivativeInfoTracker(eventBus);
        bootStrap.register(derivativeInfoTracker);

        PositionTracker positionTracker = new PositionTracker(eventBus);
        bootStrap.register(positionTracker);

        FundingPaymentTracker fundingPaymentTracker = new FundingPaymentTracker(
                positionTracker,
                eventBus,
                eventBus
        );
        bootStrap.register(fundingPaymentTracker);
        bootStrap.register(new FundingPaymentSnapshotUpdater(fundingPaymentRepository, eventBus));

        return new DerivativeExchangeConnector(
                DerivativeApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                tradingRuleRegistry,
                exchangeOrderExecutor,
                fundingInfoTracker,
                positionTracker
        );
    }

    private static RestAssistant rest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry,
            String circuitName,
            int maxRetry
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, circuitName)
                .errorClassifier(new DefaultExchangeErrorClassifier())
                .maxRetry(maxRetry)
                .build();
    }

    private static RestAssistant orderEntryRest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderEntry(Exchange.HYPERLIQUID_DERIVATIVE))
                .errorClassifier(new DefaultExchangeErrorClassifier())
                .build();
    }

    private static RestAssistant orderCancelRest(
            RestAssistant rawRestAssistant,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new RestAssistantBuilder(rawRestAssistant)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderCancel(Exchange.HYPERLIQUID_DERIVATIVE))
                .errorClassifier(new DefaultExchangeErrorClassifier())
                .maxRetry(1)
                .build();
    }
}
