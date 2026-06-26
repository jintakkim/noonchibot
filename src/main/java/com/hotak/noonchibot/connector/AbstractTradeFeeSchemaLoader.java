package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.LifecycleAware;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractTradeFeeSchemaLoader implements TradeFeeSchemaLoader, LifecycleAware {
    private final IoExecutor ioExecutor;
    protected final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    private final Map<String, TradeFeeSchema> pairFeeSchemaCache = new HashMap<>();

    @Override
    public TradeFeeSchema get(String tradingPair) {
        TradeFeeSchema schema = pairFeeSchemaCache.get(tradingPair);
        if(schema == null) {
            throw new IllegalArgumentException("Unknown trading pair: " + tradingPair);
        }
        return schema;
    }

    private void prepare() {
        Map<String, TradeFeeSchema> schemas = loadPairFeeSchema(tradingPairSymbolRegistry.getAllTradingPairs());
        pairFeeSchemaCache.putAll(schemas);
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
    public void onStart() {
        prepare();
    }

    public void start() {
        onStart();
    }

    @Override
    public void onShutdown() {
    }

    public void shutdown() {
        onShutdown();
    }
}
