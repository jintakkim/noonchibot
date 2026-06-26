package com.hotak.noonchibot.connector.binance;

import com.hotak.noonchibot.connector.binance.derivative.ApiSpec;
import com.hotak.noonchibot.connector.web.RestRequest;
import com.hotak.noonchibot.connector.web.RestResponse;
import com.hotak.noonchibot.connector.web.testutils.RestFixture;
import com.hotak.noonchibot.core.order.InFlightOrder;
import com.hotak.noonchibot.core.order.OrderType;
import com.hotak.noonchibot.core.order.TimeInForce;
import com.hotak.noonchibot.core.trade.TradeType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static com.hotak.noonchibot.testutils.FixtureUtils.ok;

public class OrderFixture {

    public static final String TRADING_PAIR = "BTC-USDT";
    public static final String EXCHANGE_SYMBOL = "BTCUSDT";
    public static final String CLIENT_ORDER_ID = "AZSdzyys6zSDNGwRim7oF8";

    public static InFlightOrder btcUsdtLimitBuyOrder() {
        return new InFlightOrder(
                CLIENT_ORDER_ID,
                TRADING_PAIR,
                OrderType.LIMIT,
                TradeType.BUY,
                new BigDecimal("0.1000"),
                new BigDecimal("40000.00"),
                Instant.parse("2026-06-01T00:00:00Z"),
                false,
                TimeInForce.GTC
        );
    }

    public static RestFixture btcUsdtLimitBuySuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.ORDER_PATH_URL)
                        .params(Map.of(
                                "symbol", EXCHANGE_SYMBOL,
                                "side", "BUY",
                                "quantity", "0.1000",
                                "type", "LIMIT",
                                "newClientOrderId", CLIENT_ORDER_ID,
                                "price", "40000.00",
                                "timeInForce", "GTC"
                        ))
                        .authRequired(true)
                        .build(),
                ok("""
                {
                    "orderId": 13677698272,
                    "symbol": "BTCUSDT",
                    "status": "NEW",
                    "clientOrderId": "AZSdzyys6zSDNGwRim7oF8",
                    "price": "40000.00",
                    "avgPrice": "0.00",
                    "origQty": "0.1000",
                    "executedQty": "0.0000",
                    "cumQty": "0.0000",
                    "cumQuote": "0.000000",
                    "timeInForce": "GTC",
                    "type": "LIMIT",
                    "reduceOnly": false,
                    "closePosition": false,
                    "side": "BUY",
                    "positionSide": "LONG",
                    "stopPrice": "0.00",
                    "workingType": "CONTRACT_PRICE",
                    "priceProtect": false,
                    "origType": "LIMIT",
                    "priceMatch": "NONE",
                    "selfTradePreventionMode": "EXPIRE_MAKER",
                    "goodTillDate": 0,
                    "updateTime": 1780302734417
                }
                """)
        );
    }

    public static RestFixture btcUsdtLimitBuyUnknownError() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.ORDER_PATH_URL)
                        .params(Map.of(
                                "symbol", "BTCUSDT",
                                "side", "BUY",
                                "quantity", "0.1000",
                                "type", "LIMIT",
                                "newClientOrderId", CLIENT_ORDER_ID,
                                "price", "40000.00",
                                "timeInForce", "GTC"
                        ))
                        .authRequired(true)
                        .build(),
                new RestResponse(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        new HttpHeaders(),
                        "Unknown error, please check your request or try again later."
                )
        );
    }

    public static RestFixture btcUsdtLimitBuyMarginInsufficientBadRequest() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.POST)
                        .pathUrl(ApiSpec.ORDER_PATH_URL)
                        .params(Map.of(
                                "symbol", EXCHANGE_SYMBOL,
                                "side", "BUY",
                                "quantity", "0.1000",
                                "type", "LIMIT",
                                "newClientOrderId", CLIENT_ORDER_ID,
                                "price", "40000.00",
                                "timeInForce", "GTC"
                        ))
                        .authRequired(true)
                        .build(),
                new RestResponse(
                        HttpStatus.BAD_REQUEST,
                        new HttpHeaders(),
                        """
                        {
                            "code": -2019,
                            "msg": "Margin is insufficient."
                        }
                        """
                )
        );
    }

    public static RestFixture btcUsdtCancelSuccess() {
        return new RestFixture(
                RestRequest.builder()
                        .method(HttpMethod.DELETE)
                        .pathUrl(ApiSpec.ORDER_PATH_URL)
                        .params(Map.of(
                                "symbol", EXCHANGE_SYMBOL,
                                "origClientOrderId", CLIENT_ORDER_ID
                        ))
                        .authRequired(true)
                        .build(),
                ok("""
            {
                "orderId": 13677698272,
                "symbol": "BTCUSDT",
                "status": "CANCELED",
                "clientOrderId": "AZSdzyys6zSDNGwRim7oF8",
                "price": "40000.00",
                "avgPrice": "0.00",
                "origQty": "0.1000",
                "executedQty": "0.0000",
                "cumQty": "0.0000",
                "cumQuote": "0.000000",
                "timeInForce": "GTC",
                "type": "LIMIT",
                "reduceOnly": false,
                "closePosition": false,
                "side": "BUY",
                "positionSide": "LONG",
                "stopPrice": "0.00",
                "workingType": "CONTRACT_PRICE",
                "priceProtect": false,
                "origType": "LIMIT",
                "priceMatch": "NONE",
                "selfTradePreventionMode": "EXPIRE_MAKER",
                "goodTillDate": 0,
                "updateTime": 1780362802254
            }
            """)
        );
    }
}
