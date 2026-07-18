package com.hotak.noonchibot.core.exchange;

/**
 * 거래소에 실제로 요청한 작업의 종류다.
 */
public enum ExchangeOperation {
    ORDER_PLACE,
    ORDER_CANCEL,
    ORDER_STATUS_QUERY
}
