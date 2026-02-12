package com.hotak.noonchibot.core.orderbook;

import com.hotak.noonchibot.core.datatype.LatencyStats;

import java.util.HashMap;
import java.util.Map;

public class OrderBookPairMetrics {
    private final String tradingPair;

    // 메시지 카운트
    private long diffsProcessed = 0; //처리된 변화
    private long diffsRejected = 0; //무시된 변화
    private long snapshotsProcessed = 0;
    private long tradesProcessed = 0;
    private long tradesRejected = 0;

    // 타임스탬프
    private double lastDiffTimestamp = 0.0; //마지막으로 들어온 데이터의 타임스탬프
    private double lastSnapshotTimestamp = 0.0;
    private double lastTradeTimestamp = 0.0;
    private double trackingStartTime = 0.0;

    // 레이턴시 통계
    private final LatencyStats diffProcessingLatency = new LatencyStats();
    private final LatencyStats snapshotProcessingLatency = new LatencyStats();
    private final LatencyStats tradeProcessingLatency = new LatencyStats();

    public OrderBookPairMetrics(String tradingPair, double startTime) {
        this.tradingPair = tradingPair;
        this.trackingStartTime = startTime;
    }

    public Map<String, Double> getMessagesPerMinute(double currentTime) {
        double elapsedMinutes = (trackingStartTime > 0) ? (currentTime - trackingStartTime) / 60.0 : 0; //트래킹 시작으로부터 흐른 시간

        Map<String, Double> rates = new HashMap<>();
        if (elapsedMinutes <= 0) {
            rates.put("diffs", 0.0);
            rates.put("snapshots", 0.0);
            rates.put("trades", 0.0);
            rates.put("total", 0.0);
            return rates;
        } //경과 시간 0일시 예외처리

        double diffsPerMin = diffsProcessed / elapsedMinutes;
        double snapshotsPerMin = snapshotsProcessed / elapsedMinutes;
        double tradesPerMin = tradesProcessed / elapsedMinutes;

        rates.put("diffs", diffsPerMin);
        rates.put("snapshots", snapshotsPerMin);
        rates.put("trades", tradesPerMin);
        rates.put("total", diffsPerMin + snapshotsPerMin + tradesPerMin);

        return rates;
    }

    public synchronized Map<String, Object> toMap(double currentTime) {
        Map<String, Object> map = new HashMap<>();

        map.put("trading_pair", tradingPair);
        map.put("diffs_processed", diffsProcessed);
        map.put("diffs_rejected", diffsRejected);
        map.put("snapshots_processed", snapshotsProcessed);
        map.put("trades_processed", tradesProcessed);
        map.put("trades_rejected", tradesRejected);

        map.put("last_diff_timestamp", lastDiffTimestamp);
        map.put("last_snapshot_timestamp", lastSnapshotTimestamp);
        map.put("last_trade_timestamp", lastTradeTimestamp);
        map.put("tracking_start_time", trackingStartTime);

        map.put("messages_per_minute", getMessagesPerMinute(currentTime));

        map.put("diff_latency", diffProcessingLatency.toMap());
        map.put("snapshot_latency", snapshotProcessingLatency.toMap());
        map.put("trade_latency", tradeProcessingLatency.toMap());

        return map;
    }

    public void recordTradeProcessed(double latency, double timestamp) {
        this.tradesProcessed++;
        this.lastSnapshotTimestamp = timestamp;
        // 레이턴시 객체가 있다면 여기서 함께 기록
        // this.tradeProcessingLatency.update(latency);
    }

    /**
     * Diff 처리를 기록
     */
    public void recordDiffProcessed(double latency, double timestamp) {
        this.diffsProcessed++;
        this.lastSnapshotTimestamp = timestamp;
        // this.diffProcessingLatency.update(latency);
    }

    /**
     * 스냅샷 처리를 기록
     */
    public void recordSnapshotProcessed(double latency, double timestamp) {
        this.snapshotsProcessed++;
        this.lastSnapshotTimestamp = timestamp;
        // this.snapshotProcessingLatency.update(latency);
    }

    //테스트용 게터 & 세터
    public String getTradingPair() { return tradingPair; }
    public long getDiffsProcessed() { return diffsProcessed; }
    public void setDiffsProcessed(long diffsProcessed) { this.diffsProcessed = diffsProcessed; }
    public long getDiffsRejected() { return diffsRejected; }
    public long getSnapshotsProcessed() { return snapshotsProcessed; }
    public void setSnapshotsProcessed(long snapshotsProcessed) { this.snapshotsProcessed = snapshotsProcessed; }
    public long getTradesProcessed() { return tradesProcessed; }
    public void setTradesProcessed(long tradesProcessed) { this.tradesProcessed = tradesProcessed; }
    public long getTradesRejected() { return tradesRejected; }
    public double getTrackingStartTime() { return trackingStartTime; }
    public void setTrackingStartTime(double trackingStartTime) { this.trackingStartTime = trackingStartTime; }
}
