package com.hotak.noonchibot.core.order;

import com.hotak.noonchibot.core.Exchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderReconciliationTaskRepository extends JpaRepository<OrderReconciliationTask, String> {
    Optional<OrderReconciliationTask> findByExchangeAndClientOrderId(Exchange exchange, String clientOrderId);

    List<OrderReconciliationTask> findByExchangeAndStatusInOrderByNextAttemptAtAsc(
            Exchange exchange,
            Collection<OrderReconciliationStatus> statuses
    );

    Optional<OrderReconciliationTask> findFirstByExchangeOrderByUpdatedAtDesc(Exchange exchange);
}
