package com.hotak.noonchibot.core.orderbook;

import tools.jackson.databind.JsonNode;

public abstract class DiffSupportingOrderBookDataSourceTest extends AbstractOrderBookDataSourceTest {
    public DiffSupportingOrderBookDataSourceTest(String quoteAsset) {
        super(quoteAsset);
    }

    protected JsonNode createDiffMessageNode(String tradingPair) {
        throw new UnsupportedOperationException("legacy createDiffMessageNode is not implemented");
    }
}
