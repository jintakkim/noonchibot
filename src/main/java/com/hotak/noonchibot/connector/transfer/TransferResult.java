package com.hotak.noonchibot.connector.transfer;

import java.math.BigDecimal;

public record TransferResult(
        boolean isSuccess,
        TransferableAccount from,
        TransferableAccount to,
        String asset,
        BigDecimal amount,
        BigDecimal fee
) {

}