package com.hotak.noonchibot.core.strategy.exposure;

import com.hotak.noonchibot.core.strategy.model.VenueOrderView;
import com.hotak.noonchibot.core.strategy.model.VenuePosition;

import com.hotak.noonchibot.core.Exchange;
import com.hotak.noonchibot.core.derivative.Position;
import com.hotak.noonchibot.core.derivative.PositionSide;
import com.hotak.noonchibot.core.order.OrderState;
import com.hotak.noonchibot.core.order.OrderView;
import com.hotak.noonchibot.core.trade.TradeType;

import java.math.BigDecimal;
import java.util.Collection;

public class VenueExposureCalculator {
    public VenueExposureSnapshot calculate(
            String strategyId,
            Exchange exchange,
            String tradingPair,
            PositionSide positionSide,
            Collection<VenuePosition> positions,
            Collection<VenueOrderView> orders
    ) {
        BigDecimal filled = positions.stream()
                .filter(position -> position.exchange() == exchange)
                .map(VenuePosition::position)
                .filter(position -> position.getTradingPair().equals(tradingPair))
                .filter(position -> position.getPositionSide() == positionSide)
                .map(this::signedPositionAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal openBuy = BigDecimal.ZERO;
        BigDecimal openSell = BigDecimal.ZERO;
        BigDecimal pendingCancelBuy = BigDecimal.ZERO;
        BigDecimal pendingCancelSell = BigDecimal.ZERO;

        for (VenueOrderView venueOrder : orders) {
            if (venueOrder.exchange() != exchange) continue;
            OrderView order = venueOrder.order();
            if (!order.tradingPair().equals(tradingPair)) continue;
            if (order.state().isTerminal()) continue;

            BigDecimal remaining = order.remainingBaseAmount();
            if (remaining == null || remaining.signum() <= 0) continue;

            if (order.state() == OrderState.PENDING_CANCEL) {
                if (order.tradeType() == TradeType.BUY) {
                    pendingCancelBuy = pendingCancelBuy.add(remaining);
                } else {
                    pendingCancelSell = pendingCancelSell.add(remaining);
                }
                continue;
            }

            if (order.tradeType() == TradeType.BUY) {
                openBuy = openBuy.add(remaining);
            } else {
                openSell = openSell.add(remaining);
            }
        }

        return new VenueExposureSnapshot(
                strategyId,
                exchange,
                tradingPair,
                positionSide,
                filled,
                openBuy,
                openSell,
                pendingCancelBuy,
                pendingCancelSell
        );
    }

    private BigDecimal signedPositionAmount(Position position) {
        if (position.getPositionSide() == PositionSide.SHORT) {
            return position.getAmount().abs().negate();
        }
        return position.getAmount();
    }
}
