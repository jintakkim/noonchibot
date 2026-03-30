package com.hotak.noonchibot.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {
}
