package com.hotak.noonchibot.connector;

import com.hotak.noonchibot.connector.auth.Authenticator;
import com.hotak.noonchibot.connector.throttle.RateLimit;
import com.hotak.noonchibot.core.orderbook.OrderBook;
import com.hotak.noonchibot.core.trade.fee.FeeEstimator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractExchangeConnector extends AbstractConnector implements ExchangeConnector {
    private final Map<String, String> symbolTradingPairMap = new HashMap<>();
    private final Map<String, String> tradingPairSymbolMap = new HashMap<>();

    public AbstractExchangeConnector(String name, FeeEstimator feeEstimator, Map<String, Map<String, BigDecimal>> balanceLimit) {
        super(name, feeEstimator, balanceLimit);
    }

    /**
     * @return 거래소 인증에 필요한 authenticator
     */
    protected abstract Authenticator getAuthenticator();

    /**
     * @return 거래소 요청 빈도 제한 규칙 리턴
     */
    protected abstract List<RateLimit> getRateLimitRules();

    /**
     * @return 거래소 도메인 리턴 ex)
     */
    protected abstract String getDomain();

    /**
     * 클라이언트에서 임의로 정하는 id에 대해 prefix를 붙인다.
     * @return 주문 id에 붙일 prefix
     */
    protected abstract String getClientOrderIdPrefix();

    /**
     * @return 거래소에서 설정한 id 최대 길이
     */
    protected abstract String getClientOrderIdMaxLength();

    /**
     * @return 지원하는 거래쌍 목록 조회 request path
     */
    protected abstract String getTradingPairRequestPath();

    /**
     * @return 거래 규칙 조회 request path
     */
    protected abstract String getTradingRulesRequestPath();

    /**
     * @return 네트워크 상태 확인 request path
     */
    protected abstract String getCheckNetworkRequestPath();

    /**
     * @return 지원하는 거래쌍 목록
     */
    protected abstract List<String> getTradingPairs();

    /**
     * @return 거래소의 취소 요청이 동기적으로 처리 되는지 여부
     */
    protected abstract boolean isCancelRequestProcessSynchronously();

    @Override
    public List<String> getAllTradingPairs() {
        return new ArrayList<>(symbolTradingPairMap.values());
    }

    public String getExchangeSymbol(String tradingPair) {
        return tradingPairSymbolMap.get(tradingPair);
    }

    public String getTradingPair(String symbol) {
        return symbolTradingPairMap.get(symbol);
    }

    /**
     * 최우선 호가 리턴
     */
    public BigDecimal getBestPrice(String tradingPair, boolean isBuy) {
        OrderBook orderBook = getOrderBook(tradingPair);
        BigDecimal topPrice = orderBook.getBestPrice(isBuy);
        return quantizeOrderPrice(tradingPair, topPrice);
    }

    public abstract OrderBook getOrderBook(String tradingPair);

    public BigDecimal getMidPrice(String tradingPair) {
        return getBestPrice(tradingPair, true).add(getBestPrice(tradingPair, false)).divide(BigDecimal.TWO, RoundingMode.HALF_UP);
    }

    public abstract BigDecimal getLastTradedPrice(String tradingPair);
}
