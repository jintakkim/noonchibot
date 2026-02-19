package com.hotak.noonchibot.core.order;

public enum OrderState {
    PENDING_CREATE,
    OPEN,
    PENDING_CANCEL,
    CANCELED,
    PARTIALLY_FILLED,
    FILLED,
    FAILED,
    PENDING_APPROVAL,
    APPROVED,
    CREATED,
    COMPLETED;
}
