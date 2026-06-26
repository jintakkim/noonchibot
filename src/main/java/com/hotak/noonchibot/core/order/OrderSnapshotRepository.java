package com.hotak.noonchibot.core.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, String> {

}
