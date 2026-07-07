package com.hotak.noonchibot.client.pricegap;

import com.hotak.noonchibot.core.pricegap.history.PriceGapHistory;
import com.hotak.noonchibot.core.pricegap.history.PriceGapHistoryTracker;
import com.hotak.noonchibot.core.pricegap.history.PriceGapTimeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@RequestMapping("/api/price-gaps")
@RequiredArgsConstructor
@Profile("!test")
public class PriceGapHistoryController {
    private final PriceGapHistoryTracker tracker;

    @GetMapping("/{tradingPair}/history")
    public ResponseEntity<PriceGapHistory> history(
            @PathVariable String tradingPair,
            @RequestParam(defaultValue = "ONE_DAY") PriceGapTimeline timeline
    ) {
        return tracker.history(tradingPair, timeline)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
