package com.hotak.noonchibot.client.funding;

import com.hotak.noonchibot.core.derivative.funding.FundingGapHistory;
import com.hotak.noonchibot.core.derivative.funding.FundingGapHistoryService;
import com.hotak.noonchibot.core.derivative.funding.FundingGapSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@Profile("!test")
@RequestMapping("/api/funding-gaps")
@RequiredArgsConstructor
public class FundingGapHistoryController {
    private final FundingGapHistoryService historyService;

    @GetMapping
    public List<FundingGapSummary> summaries() {
        return historyService.summaries();
    }

    @GetMapping("/{asset}/history")
    public FundingGapHistory history(
            @PathVariable String asset
    ) {
        try {
            return historyService.history(asset);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
}
