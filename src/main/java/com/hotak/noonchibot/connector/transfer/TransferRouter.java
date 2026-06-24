package com.hotak.noonchibot.connector.transfer;

import java.util.concurrent.CompletableFuture;

public interface TransferRouter {
    CompletableFuture<TransferResult> handle(TransferRoute route);
    boolean canHandle(TransferRoute route);
}
