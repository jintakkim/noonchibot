package com.hotak.noonchibot.core.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Phases {

    public static final int TIME_SYNCHRONIZATION = 0;

    public static final int ORDER_BOOK_DATASOURCE_SETUP = 90;
    public static final int ORDER_TRACKER_SETUP = 100;
    public static final int ORDER_BOOK_TRACKER_SETUP = 100;
    public static final int ACCOUNT_BALANCE_TRACKER_SETUP = 100;
    public static final int FUNDING_INFO_TRACKER_SETUP = 100;
    public static final int DERIVATIVE_INFO_TRACKER_SETUP = 100;
    public static final int POSITION_TRACKER_SETUP = 100;
    public static final int FUNDING_PAYMENT_TRACKER_SETUP = 110;
    public static final int SNAPSHOT_UPDATER_SETUP = 120;

    public static final int ORDER_STATUS_DATASOURCE_SETUP = 150;
    public static final int TRADE_DATASOURCE_SETUP = 150;
    public static final int DERIVATIVE_INFO_DATASOURCE_SETUP = 150;
    public static final int ORDER_RESTORE = 180;

    public static final int TRADING_RULE_SETUP = 200;
    public static final int BALANCE_SETUP = 200;
    public static final int TRANSFER_SETUP = 200;
    public static final int FUNDING_INFO_DATASOURCE_SETUP = 200;
    public static final int USER_STREAM_DATASOURCE_SETUP = 200;

    public static final int ORDER_EXECUTOR_SETUP = 300;
    public static final int ORDER_STATUS_POLLING = 350;
    public static final int TRADE_POLLING = 350;
}
