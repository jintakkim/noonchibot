package com.hotak.noonchibot.core.event.internal.derivative;

import com.hotak.noonchibot.core.derivative.FundingPayment;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public sealed interface FundingPaymentEvent extends CoreEvent {
    record Received(FundingPayment payment) implements FundingPaymentEvent {}

    record SnapshotUpdateRequested(FundingPayment payment) implements FundingPaymentEvent {}
}
