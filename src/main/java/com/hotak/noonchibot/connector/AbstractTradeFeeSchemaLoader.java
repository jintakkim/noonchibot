package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.core.IoExecutor;
import com.hotak.noonchibot.core.MainExecutor;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchema;
import com.hotak.noonchibot.core.trade.fee.TradeFeeSchemaLoader;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RequiredArgsConstructor
public abstract class AbstractTradeFeeSchemaLoader implements TradeFeeSchemaLoader {
    private final IoExecutor ioExecutor;
    private final MainExecutor mainExecutor;
    private final TradingPairSymbolRegistry tradingPairSymbolRegistry;

    private final Map<String, CompletableFuture<TradeFeeSchema>> pairFeeSchemaCache = new HashMap<>();

    @Override
    public CompletableFuture<TradeFeeSchema> get(String tradingPair) {
        return pairFeeSchemaCache.computeIfAbsent(tradingPair, this::loadPairFeeSchema);
    }

    private CompletableFuture<TradeFeeSchema> loadPairFeeSchema(String tradingPair) {
        String symbol = tradingPairSymbolRegistry.convertTradingPairToExchangeSymbol(tradingPair);
        return ioExecutor.submitCompletable(() -> fetchPairFeeSchema(symbol))
                .thenApply(this::parseSchema)
                .exceptionallyAsync(ex -> {
                    pairFeeSchemaCache.remove(tradingPair);
                    throw new CompletionException(ex);
                }, mainExecutor);
    }

    protected abstract JsonNode fetchPairFeeSchema(String symbol);
    protected abstract TradeFeeSchema parseSchema(JsonNode schema);
}
