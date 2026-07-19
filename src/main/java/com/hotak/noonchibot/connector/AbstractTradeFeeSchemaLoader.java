package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractTradeFeeSchemaLoader implements TradeFeeSchemaLoader, SmartLifecycle {
    private final IoExecutor ioExecutor;
    protected final TradingPairSymbolRegistry tradingPairSymbolRegistry;
    private volatile boolean running = false;

    private volatile Map<String, TradeFeeSchema> pairFeeSchemaSnapshot = Map.of();

    @Override
    public TradeFeeSchema get(String tradingPair) {
        TradeFeeSchema schema = pairFeeSchemaSnapshot.get(tradingPair);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        }
        return schema;
    }

    private void prepare() {
        Map<String, TradeFeeSchema> loaded = loadPairFeeSchema(
                tradingPairSymbolRegistry.getAllTradingPairs()
        );
        pairFeeSchemaSnapshot = Map.copyOf(loaded);
    }

    /**
     * override this method if exchange api support bulk-pair-fetch
     */
    protected Map<String, TradeFeeSchema> loadPairFeeSchema(List<String> tradingPairs) {
        List<CompletableFuture<Map.Entry<String, TradeFeeSchema>>> futures = tradingPairs.stream()
                .map(pair -> ioExecutor
                        .submitCompletable(() -> loadPairFeeSchema(pair))
                        .thenApply(schema -> Map.entry(pair, schema)))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private TradeFeeSchema loadPairFeeSchema(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        JsonNode res = fetchPairFeeSchema(symbol);
        return parseSchema(res);
    }

    protected abstract JsonNode fetchPairFeeSchema(String symbol);
    protected abstract TradeFeeSchema parseSchema(JsonNode schema);

    @Override
    public void start() {
        prepare();
        running = true;
    }

    @Override
    public void stop() {
        running = false;

    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
