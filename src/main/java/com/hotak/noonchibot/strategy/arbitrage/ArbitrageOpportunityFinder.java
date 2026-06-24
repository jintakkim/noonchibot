package com.hotak.noonchibot.strategy.arbitrage;

import com.hotak.noonchibot.core.derivative.FundingInfo;
import com.hotak.noonchibot.core.derivative.FundingInfoTracker;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.orderbook.VWAPForVolumeQueryResult;
import com.hotak.noonchibot.core.trade.TradeFeeSchema;
import com.hotak.noonchibot.core.utils.TradingPairUtils;
import com.hotak.noonchibot.strategy.DerivativeExchangeAdapter;
import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.strategy.ExchangeAdapter;
import com.hotak.noonchibot.strategy.PriceNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class ArbitrageOpportunityFinder {
    private final ArbitrageWatchlist watchlist;
    private final Map<Exchange, ExchangeAdapter> gateways;
    private final PriceNormalizer priceNormalizer;


    public FindResult find(FindRequest request) {
        BigDecimal orderAmount = request.orderAmount();
        List<ArbitrageOpportunity> opportunities = new ArrayList<>();

        for (String baseAsset : watchlist.getAllBaseAssets()) {
            List<ArbitrageWatchlist.Entity> entities = watchlist.getByBaseAsset(baseAsset);
            List<ExchangeQuote> quotes = collectQuotes(entities, orderAmount);
            if (quotes.size() < 2) continue;
            List<GapPair> gaps = calculateGaps(baseAsset, quotes);
            opportunities.add(new ArbitrageOpportunity(baseAsset, quotes, gaps));
        }

        return new FindResult(orderAmount, opportunities, Instant.now());
    }

    private List<ExchangeQuote> collectQuotes(List<ArbitrageWatchlist.Entity> entities, BigDecimal orderAmount) {
        List<ExchangeQuote> quotes = new ArrayList<>();
        for (ArbitrageWatchlist.Entity entity : entities) {
            ExchangeAdapter gw = gateways.get(entity.exchange());
            OrderBook book = gw.getOrderBookTracker().findOrderBook(entity.tradingPair()).orElse(null);
            if (book == null) {
                log.warn("[{}] {} 오더북 미초기화", entity.exchange(), entity.tradingPair());
                continue;
            }
            VWAPForVolumeQueryResult buyResult = book.getVWAPForQuoteVolume(true, orderAmount);
            VWAPForVolumeQueryResult sellResult = book.getVWAPForQuoteVolume(false, orderAmount);


            BigDecimal fundingRate = null;
            Instant nextFundingTime = null;
            BigDecimal annualized = null;

            if (!gw.isSpot() && gw instanceof DerivativeExchangeAdapter dgw) {
                FundingInfoTracker fundingInfoTracker = dgw.getFundingInfoTracker();
                FundingInfo fundingInfo = fundingInfoTracker.getFundingInfo(entity.tradingPair());
                if(fundingInfo != null) {
                    nextFundingTime = fundingInfo.getNextFundingTime();
                    fundingRate = fundingInfo.getFundingRate();
                    annualized = fundingInfo.getAnnualizedFundingRate();
                }
            }
            String quoteAsset = TradingPairUtils.getQuote(entity.tradingPair());
            quotes.add(new ExchangeQuote(
                    gw.getPlatformName(),
                    gw.isSpot(),
                    quoteAsset,
                    buyResult.vwapPrice(),
                    sellResult.vwapPrice(),
                    priceNormalizer.getTargetAsset(),
                    priceNormalizer.normalizeValue(quoteAsset, buyResult.vwapPrice()),
                    priceNormalizer.normalizeValue(quoteAsset, buyResult.vwapPrice()),
                    fundingRate,
                    nextFundingTime,
                    annualized
            ));
        }
        return quotes;
    }

    private List<GapPair> calculateGaps(String baseAsset, List<ExchangeQuote> quotes) {
        List<GapPair> allGaps = new ArrayList<>();

        for (int i = 0; i < quotes.size(); i++) {
            for (int j = i + 1; j < quotes.size(); j++) {
                ExchangeQuote a = quotes.get(i);
                ExchangeQuote b = quotes.get(j);
                // A에서 사고 B에서 판다
                allGaps.add(calculateGapPair(baseAsset, a, b));
                // B에서 사고 A에서 판다
                allGaps.add(calculateGapPair(baseAsset, b, a));
            }
        }
        return allGaps;
    }

    private GapPair calculateGapPair(String baseAsset, ExchangeQuote buyer, ExchangeQuote seller) {
        ExchangeAdapter buyerGw = gateways.get(Exchange.from(buyer.exchangeId()));
        ExchangeAdapter sellerGw = gateways.get(Exchange.from(seller.exchangeId()));
        if(buyerGw == null || sellerGw == null) {
            log.warn("Can't calculate gap, reason: No ExchangeGateway registered for {}", buyerGw == null ? buyer.exchangeId() : seller.exchangeId());
            return null;
        }
        BigDecimal buyPrice = buyer.effectiveNormalizedBuyPrice();
        BigDecimal sellPrice = seller.effectiveNormalizedSellPrice();
        BigDecimal rawGap = sellPrice.subtract(buyPrice).divide(buyPrice, MathContext.DECIMAL64);

        TradeFeeSchema buyFeeSchema = buyerGw.getTradeFeeSchemaLoader().get(TradingPairUtils.getTradingPair(baseAsset, buyer.quoteAsset()));
        TradeFeeSchema sellFeeSchema = sellerGw.getTradeFeeSchemaLoader().get(TradingPairUtils.getTradingPair(baseAsset, seller.quoteAsset()));
        BigDecimal buyMakerFee = buyFeeSchema.makerFeeRate();
        BigDecimal buyTakerFee = buyFeeSchema.takerFeeRate();
        BigDecimal sellMakerFee = sellFeeSchema.makerFeeRate();
        BigDecimal sellTakerFee = sellFeeSchema.takerFeeRate();

        return new GapPair(
                buyer.exchangeId(),
                seller.exchangeId(),
                rawGap,
                rawGap.subtract(buyTakerFee).subtract(sellMakerFee),
                rawGap.subtract(buyMakerFee).subtract(sellMakerFee),
                rawGap.subtract(buyMakerFee).subtract(sellTakerFee),
                rawGap.subtract(buyTakerFee).subtract(sellTakerFee)
        );
    }
}
