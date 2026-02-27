package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.LatencyStats;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
public class OrderBookPairMetrics {
    private final String tradingPair;

    private long diffsProcessed = 0;
    private long diffsRejected = 0;
    private long snapshotsProcessed = 0;
    private long tradesProcessed = 0;
    private long tradesRejected = 0;

    private Instant lastDiffTimestamp;
    private Instant lastSnapshotTimestamp;
    private Instant lastTradeTimestamp;
    private Instant trackingStartTime;

    private final LatencyStats diffProcessingLatency = new LatencyStats();
    private final LatencyStats snapshotProcessingLatency = new LatencyStats();
    private final LatencyStats tradeProcessingLatency = new LatencyStats();

    public OrderBookPairMetrics(String tradingPair, Instant startTime) {
        this.tradingPair = tradingPair;
        this.trackingStartTime = (startTime != null) ? startTime : Instant.now();
    }

    public Map<String, Double> getMessagesPerMinute(Instant currentTime) {
        if (trackingStartTime == null) {
            Map<String, Double> emptyRates = new HashMap<>();
            emptyRates.put("diffs", 0.0);
            emptyRates.put("snapshots", 0.0);
            emptyRates.put("trades", 0.0);
            emptyRates.put("total", 0.0);
            return emptyRates;
        }

        Duration duration = Duration.between(trackingStartTime, currentTime);
        double elapsedMinutes = (double) duration.toNanos() / TimeUnit.MINUTES.toNanos(1);

        Map<String, Double> rates = new HashMap<>();

        if (elapsedMinutes <= 0) {
            rates.put("diffs", 0.0);
            rates.put("snapshots", 0.0);
            rates.put("trades", 0.0);
            rates.put("total", 0.0);
            return rates;
        }

        double diffsPerMin = diffsProcessed / elapsedMinutes;
        double snapshotsPerMin = snapshotsProcessed / elapsedMinutes;
        double tradesPerMin = tradesProcessed / elapsedMinutes;

        rates.put("diffs", diffsPerMin);
        rates.put("snapshots", snapshotsPerMin);
        rates.put("trades", tradesPerMin);
        rates.put("total", diffsPerMin + snapshotsPerMin + tradesPerMin);

        return rates;
    }

    public synchronized void recordDiffProcessed(Duration latency, Instant now) {
        this.diffsProcessed++;
        this.lastDiffTimestamp = now; // 마지막 성공 시점 기록
        this.diffProcessingLatency.record(latency); // 지연 시간 통계 기록
    }

    public synchronized void incrementDiffsRejected() {
        this.diffsRejected++;
    }

    public synchronized void recordSnapshotProcessed(Duration latency, Instant now) {
        this.snapshotsProcessed++;
        this.lastSnapshotTimestamp = now;
        this.snapshotProcessingLatency.record(latency);
    }

    public synchronized void recordTradeProcessed(Duration latency, Instant now) {
        this.tradesProcessed++;
        this.lastTradeTimestamp = now;
        this.tradeProcessingLatency.record(latency);
    }

    public synchronized void incrementTradesRejected() {
        this.tradesRejected++;
    }
}
