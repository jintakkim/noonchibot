package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.LatencyStats;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public class OrderBookTrackerMetrics {
    private long totalDiffsProcessed = 0;
    private long totalDiffsRejected = 0;
    private long totalDiffsQueued = 0;
    private long totalSnapshotsProcessed = 0;
    private long totalSnapshotsRejected = 0;
    private long totalTradesProcessed = 0;
    private long totalTradesRejected = 0;

    private double trackerStartTime = 0.0;

    private final LatencyStats diffProcessingLatency = new LatencyStats();
    private final LatencyStats snapshotProcessingLatency = new LatencyStats();
    private final LatencyStats tradeProcessingLatency = new LatencyStats();

    private final Map<String, OrderBookPairMetrics> perPairMetrics = new ConcurrentHashMap<>();

    public OrderBookTrackerMetrics() {
        this.trackerStartTime = 0.0;
    }

    public OrderBookPairMetrics getOrCreatePairMetrics(String tradingPair) {
        return perPairMetrics.computeIfAbsent(tradingPair, pair ->
                new OrderBookPairMetrics(pair, System.nanoTime() / 1_000_000_000.0)
        );
    }

    public void removePairMetrics(String tradingPair) {
        perPairMetrics.remove(tradingPair);
    }

    public Map<String, Double> getMessagesPerMinute(double currentTime) {
        double elapsedMinutes = (trackerStartTime > 0) ? (currentTime - trackerStartTime) / 60.0 : 0;

        Map<String, Double> rates = new HashMap<>();
        if (elapsedMinutes <= 0) {
            rates.put("diffs", 0.0);
            rates.put("snapshots", 0.0);
            rates.put("trades", 0.0);
            rates.put("total", 0.0);
            return rates;
        }

        double diffsPerMin = totalDiffsProcessed / elapsedMinutes;
        double snapshotsPerMin = totalSnapshotsProcessed / elapsedMinutes;
        double tradesPerMin = totalTradesProcessed / elapsedMinutes;

        rates.put("diffs", diffsPerMin);
        rates.put("snapshots", snapshotsPerMin);
        rates.put("trades", tradesPerMin);
        rates.put("total", diffsPerMin + snapshotsPerMin + tradesPerMin);

        return rates;
    }

    public synchronized Map<String, Object> toMap() {
        double currentTime = System.nanoTime() / 1_000_000_000.0;
        Map<String, Object> map = new HashMap<>();

        map.put("total_diffs_processed", totalDiffsProcessed);
        map.put("total_diffs_rejected", totalDiffsRejected);
        map.put("total_diffs_queued", totalDiffsQueued);
        map.put("total_snapshots_processed", totalSnapshotsProcessed);
        map.put("total_trades_processed", totalTradesProcessed);

        map.put("uptime_seconds", (trackerStartTime > 0) ? (currentTime - trackerStartTime) : 0);
        map.put("messages_per_minute", getMessagesPerMinute(currentTime));

        map.put("diff_latency", diffProcessingLatency.toMap());
        map.put("snapshot_latency", snapshotProcessingLatency.toMap());
        map.put("trade_latency", tradeProcessingLatency.toMap());

        Map<String, Map<String, Object>> perPairData = new HashMap<>();
        for (Map.Entry<String, OrderBookPairMetrics> entry : perPairMetrics.entrySet()) {
            perPairData.put(entry.getKey(), entry.getValue().toMap(currentTime));
        }
        map.put("per_pair_metrics", perPairData);

        return map;
    }

    public void incrementTotalDiffsQueued() {
        this.totalDiffsQueued++;
    }

    public void incrementTotalDiffsRejected() {
        this.totalDiffsRejected++;
    }

    public void incrementTotalSnapshotsRejected() {
        this.totalSnapshotsRejected++;
    }

    public void incrementTotalTradesRejected() {
        this.totalTradesRejected++;
    }

    public void recordDiffProcessed(double latency) { this.totalDiffsProcessed++; }

    public void recordSnapshotProcessed(double latency) {
        this.totalSnapshotsProcessed++;
    }

    public void recordTradeProcessed(double latency) {
        this.totalTradesProcessed++;
    }
}
