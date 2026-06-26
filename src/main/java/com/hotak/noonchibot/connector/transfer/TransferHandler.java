package com.hotak.noonchibot.connector.transfer;

public interface TransferHandler {
    boolean canHandle(TransferRoute route);
    TransferResult execute(TransferRoute route);
}
