package com.hotak.noonchibot.core.event.internal.transfer;

import com.hotak.noonchibot.connector.transfer.TransferResult;
import com.hotak.noonchibot.connector.transfer.TransferRoute;
import com.hotak.noonchibot.core.event.internal.CoreEvent;

public sealed interface TransferEvent extends CoreEvent {
    record Requested(TransferRoute route) implements TransferEvent {}

    record Completed(TransferResult result) implements TransferEvent {}

    record Failed(TransferRoute route, Throwable cause) implements TransferEvent {}
}
