package com.hotak.noonchibot.core.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Phases {

    public static int TIME_SYNCHRONIZATION = 0;
    public static int WEBSOCKET_CONNECTION = 0;

    /**
     * fundingInfo related
     */
    public static int FUNDING_INFO_DATASOURCE_SETUP = 200;

    /**
     * derivative info related
     */
    public static int DERIVATIVE_INFO_SETUP = 200;

    public static int TRADING_RULE_SETUP = 200;

    public static int TRADE_DATASOURCE_SETUP = 150;
    public static int TRADE_POLLING = 200;

    public static int BALANCE_SETUP = 200;
    public static int TRANSFER_SETUP = 200;

    public static int ORDER_BOOK_TRACKER_SETUP = 190;
    public static int ORDER_BOOK_DATASOURCE_SETUP = 200;

    public static int ORDER_EXECUTOR_SETUP = 300;
    public static int ORDER_TRACKER_SETUP = 100;
    public static int ORDER_STATUS_DATASOURCE_SETUP = 150;
    public static int ORDER_STATUS_POLLING = 200;

    /**
     * ORDER_TRACKER_SETUP 보다 커야함
     */
    public static int USER_STREAM_DATASOURCE_SETUP = 200;
}
