package com.hotak.noonchibot.connector.transfer;

import com.hotak.noonchibot.core.Exchange;

public record TransferableAccount(
        Exchange exchange,
        String identifier,
        String role // nullable
) {
}
