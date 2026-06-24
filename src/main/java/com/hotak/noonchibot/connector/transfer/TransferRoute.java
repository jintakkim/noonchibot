package com.hotak.noonchibot.connector.transfer;

import java.math.BigDecimal;

public record TransferRoute(
    TransferableAccount from,
    TransferableAccount to,
    String asset,
    BigDecimal amount
) {}
