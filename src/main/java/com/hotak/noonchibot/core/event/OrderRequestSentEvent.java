package com.hotak.noonchibot.core.event;

import com.hotak.noonchibot.core.order.InFlightOrder;

/**
 * 거래소에 거래 요청을 보낸 후 발생되는 이벤트
 * 거래 요청이 정상적으로 수행되었는지는 보장하지 않는다.
 */
public record OrderRequestSentEvent(
        InFlightOrder inFlightOrder
) implements ExchangeEvent {
}
