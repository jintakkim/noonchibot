package com.hotak.noonchibot.strategy.arbitrage;

import com.hotak.noonchibot.core.Exchange;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class ArbitrageWatchlist {
    public record Entity(Exchange exchange, String tradingPair) {
    }

    /**
     * baseAsset - List(Entity) Map
     */
    private final Map<String, List<Entity>> group;

    public Set<String> getAllBaseAssets() {
        return group.keySet();
    }

    public List<Entity> getByBaseAsset(String baseAsset) {
        return group.get(baseAsset);
    }


}
