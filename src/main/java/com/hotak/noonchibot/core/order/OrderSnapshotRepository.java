package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, String> {
    List<OrderSnapshot> findByExchangeAndStateNotIn(Exchange exchange, Collection<OrderState> terminalStates);
}
