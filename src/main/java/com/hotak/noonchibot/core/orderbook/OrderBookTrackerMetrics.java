package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.LatencyStats;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    private Instant trackerStartTime;

    private final LatencyStats diffProcessingLatency = new LatencyStats();
    private final LatencyStats snapshotProcessingLatency = new LatencyStats();
    private final LatencyStats tradeProcessingLatency = new LatencyStats();

    private final Map<String, OrderBookPairMetrics> perPairMetrics = new ConcurrentHashMap<>();

    public OrderBookTrackerMetrics() {
        this.trackerStartTime = Instant.now();
    }

    public OrderBookPairMetrics getOrCreatePairMetrics(String tradingPair) {
        return perPairMetrics.computeIfAbsent(tradingPair, pair ->
                new OrderBookPairMetrics(pair, Instant.now())
        );
    }

    public void removePairMetrics(String tradingPair) {
        perPairMetrics.remove(tradingPair);
    }

    public Map<String, Double> getMessagesPerMinute(Instant currentTime) {
        Duration duration = Duration.between(trackerStartTime, currentTime);
        double elapsedMinutes = duration.toNanos() / (double) TimeUnit.MINUTES.toNanos(1);

        Map<String, Double> rates = new HashMap<>();
        if (elapsedMinutes <= 0) {
            rates.put("diffs", 0.0);
            rates.put("snapshots", 0.0);
            rates.put("trades", 0.0);
            rates.put("total", 0.0);
            return rates;
        }

        rates.put("diffs", totalDiffsProcessed / elapsedMinutes);
        rates.put("snapshots", totalSnapshotsProcessed / elapsedMinutes);
        rates.put("trades", totalTradesProcessed / elapsedMinutes);
        rates.put("total", (totalDiffsProcessed + totalSnapshotsProcessed + totalTradesProcessed) / elapsedMinutes);

        return rates;
    }

    public synchronized void recordDiffProcessed(Duration latency) {
        this.totalDiffsProcessed++;
        this.diffProcessingLatency.record(latency);
    }

    public synchronized void recordSnapshotProcessed(Duration latency) {
        this.totalSnapshotsProcessed++;
        this.snapshotProcessingLatency.record(latency);
    }

    public synchronized void recordTradeProcessed(Duration latency) {
        this.totalTradesProcessed++;
        this.tradeProcessingLatency.record(latency);
    }

    public void incrementTotalDiffsQueued() { this.totalDiffsQueued++; }
    public void incrementTotalDiffsRejected() { this.totalDiffsRejected++; }
    public void incrementTotalSnapshotsRejected() { this.totalSnapshotsRejected++; }
    public void incrementTotalTradesRejected() { this.totalTradesRejected++; }
}
