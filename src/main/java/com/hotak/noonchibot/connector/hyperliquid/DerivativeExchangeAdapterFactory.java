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
import com.hotak.noonchibot.core.derivative.funding.FundingInfoTracker;
import com.hotak.noonchibot.core.derivative.funding.FundingHistoryProperties;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentRepository;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentSnapshotUpdater;
import com.hotak.noonchibot.core.derivative.funding.FundingPaymentTracker;
import com.hotak.noonchibot.core.derivative.PositionTracker;
import com.hotak.noonchibot.core.event.EventBus;
import com.hotak.noonchibot.core.event.ExecutionPolicy;
import com.hotak.noonchibot.core.event.internal.derivative.FundingInfoEvent;
import com.hotak.noonchibot.core.event.internal.order.OrderEvent;
import com.hotak.noonchibot.core.order.ExchangeOrderExecutor;
import com.hotak.noonchibot.core.order.OrderSnapshotRepository;
import com.hotak.noonchibot.core.order.OrderSnapshotUpdater;
import com.hotak.noonchibot.core.order.OrderRecoveryBootstrap;
import com.hotak.noonchibot.core.order.OrderTracker;
import com.hotak.noonchibot.core.order.TradeRepository;
import com.hotak.noonchibot.core.orderbook.OrderBookTracker;
import com.hotak.noonchibot.core.strategy.safety.TradingSafetyController;
import com.hotak.noonchibot.core.resilience.CircuitBreakerNames;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.msgpack.jackson.dataformat.MessagePackMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.context.ApplicationEventPublisher;
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
            ApplicationEventPublisher applicationEventPublisher,
            ObjectMapper objectMapper,
            MessagePackMapper messagePackMapper,
            WebSocketClient webSocketClient,
            TradeRepository tradeRepository,
            FundingPaymentRepository fundingPaymentRepository,
            TradingSafetyController tradingSafetyController,
            FundingHistoryProperties fundingHistoryProperties,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        EventBus eventBus = new EventBus();
        tradingSafetyController.connect(eventBus);
        boolean testnet = props.network().isTestnet();
        String restBaseUrl = testnet ? DerivativeApiSpec.TESTNET_BASE_URL : DerivativeApiSpec.BASE_URL;
        String websocketUrl = testnet ? DerivativeApiSpec.TESTNET_WS_URL : DerivativeApiSpec.WS_URL;
        RestClient restClient = RestClient.builder()
                .baseUrl(restBaseUrl)
                .build();
        TradingPairSymbolRegistry tradingPairSymbolRegistry = new SimpleTradingPairSymbolRegistry(
                props.derivative().tradingPairSymbolMap()
        );
        AsyncThrottler throttler = new AsyncThrottlerImpl(DerivativeApiSpec.RATE_LIMITS, ioExecutor);
        HyperliquidAuthenticator authenticator = new HyperliquidAuthenticator(
                objectMapper,
                messagePackMapper,
                null,
                !testnet,
                props.address(),
                props.secret()
        );
        RestAssistant baseRestAssistant = new RestAssistantImpl(
                restClient,
                List.of(new HyperliquidRateLimitPreProcessor()),
                List.of(),
                authenticator,
                throttler,
                objectMapper
        );
        FundingRateHistoryDataSourceImpl fundingRateHistoryDataSource =
                new FundingRateHistoryDataSourceImpl(
                        rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.funding(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                        tradingPairSymbolRegistry
                );
        bootStrap.register(new FundingRateHistoryEventHandler(fundingRateHistoryDataSource, eventBus, eventBus));
        WsAssistantImpl wsAssistant = new WsAssistantImpl(webSocketClient, new WebSocketHttpHeaders(), List.of(), List.of(), objectMapper, authenticator);

        OrderBookDataSource orderBookDataSource = new OrderBookDataSource(
                wsAssistant,
                objectMapper,
                ioExecutor,
                taskScheduler,
                applicationEventPublisher,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderBook(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                tradingPairSymbolRegistry,
                websocketUrl,
                eventBus,
                eventBus
        );
        bootStrap.register(orderBookDataSource);

        OrderTracker orderTracker = new OrderTracker(eventBus, tradeRepository, orderSnapshotRepository, eventBus);
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
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradeFee(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                props.address()
        );
        bootStrap.register(feeSchemaLoader);

        UserStreamDataSource userStreamDataSource = new UserStreamDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                props.address(),
                tradingPairSymbolRegistry,
                eventBus,
                websocketUrl
        );
        bootStrap.register(userStreamDataSource);

        RestBalanceDataSource balanceDataSource = new RestBalanceDataSource(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.balance(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                taskScheduler,
                props.address()
        );
        bootStrap.register(balanceDataSource);

        HLTradingRuleRegistry tradingRuleRegistry = new HLTradingRuleRegistry(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.tradingRules(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                tradingPairSymbolRegistry,
                taskScheduler
        );
        bootStrap.register(tradingRuleRegistry);

        OrderClientImpl orderClient = new OrderClientImpl(
                orderEntryRest(baseRestAssistant, circuitBreakerRegistry),
                orderCancelRest(baseRestAssistant, circuitBreakerRegistry),
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
        bootStrap.register(new OrderSnapshotUpdater(orderSnapshotRepository, eventBus));

        OrderStatusDataSource orderStatusDataSource = new OrderStatusDataSource(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.orderStatus(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                eventBus,
                props.address()
        );
        bootStrap.register(orderStatusDataSource);
        TradeDataSource tradeDataSource = new TradeDataSource(
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.trades(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                eventBus,
                props.address()
        );
        bootStrap.register(tradeDataSource);
        bootStrap.register(new OrderRecoveryBootstrap(
                Exchange.HYPERLIQUID_DERIVATIVE,
                orderSnapshotRepository,
                orderStatusDataSource,
                tradeDataSource,
                orderTracker
        ));
        bootStrap.register(new OrderStatusPoller(eventBus, eventBus, orderTracker, taskScheduler));
        bootStrap.register(new TradePoller(eventBus, eventBus, taskScheduler, orderTracker));
        DeriviativeInnerTransfer innerTransfer = new DeriviativeInnerTransfer(
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.transfer(Exchange.HYPERLIQUID_DERIVATIVE), 1)
        );
        bootStrap.register(new TransferDispatcher(List.of(innerTransfer), eventBus, eventBus));

        HyperliquidWsFundingInfoDataSource fundingInfoDataSource = new HyperliquidWsFundingInfoDataSource(
                wsAssistant,
                objectMapper,
                taskScheduler,
                applicationEventPublisher,
                tradingPairSymbolRegistry,
                rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.funding(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                eventBus,
                tradingPairSymbolRegistry.getAllTradingPairs(),
                websocketUrl
        );
        bootStrap.register(fundingInfoDataSource);

        FundingInfoTracker fundingInfoTracker = new FundingInfoTracker(
                Exchange.HYPERLIQUID_DERIVATIVE,
                Duration.ofHours(1),
                "USDC",
                tradingPairSymbolRegistry,
                eventBus,
                eventBus,
                applicationEventPublisher,
                fundingRateHistoryDataSource,
                taskScheduler,
                fundingHistoryProperties
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
                Exchange.HYPERLIQUID_DERIVATIVE,
                DerivativeApiSpec.PLATFORM_NAME,
                orderTracker,
                orderBookTracker,
                accountBalanceTracker,
                feeSchemaLoader,
                tradingRuleRegistry,
                exchangeOrderExecutor,
                eventBus,
                eventBus,
                new HyperliquidPriceCandleDataSource(
                        rest(baseRestAssistant, circuitBreakerRegistry, CircuitBreakerNames.rest(Exchange.HYPERLIQUID_DERIVATIVE), 2),
                        tradingPairSymbolRegistry
                ),
                fundingInfoTracker,
                positionTracker
        );
    }

    private static RestAssistant rest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            String circuitName,
            int maxAttempt
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, circuitName)
                .errorClassifier(new SimpleExchangeErrorClassifier())
                .maxAttempt(maxAttempt)
                .build();
    }

    private static RestAssistant orderEntryRest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderEntry(Exchange.HYPERLIQUID_DERIVATIVE))
                .errorClassifier(new SimpleExchangeErrorClassifier())
                .build();
    }

    private static RestAssistant orderCancelRest(
            RestAssistant delegate,
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return new RestAssistantBuilder(delegate)
                .circuit(circuitBreakerRegistry, CircuitBreakerNames.orderCancel(Exchange.HYPERLIQUID_DERIVATIVE))
                .errorClassifier(new SimpleExchangeErrorClassifier())
                .maxAttempt(1)
                .build();
    }
}
