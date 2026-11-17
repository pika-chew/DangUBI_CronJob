package com.pikachew.dangUBI.domain.outbox.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

	/** 한 대상의 이벤트를 기록된 순서대로 */
	List<Outbox> findAllByAggregateTypeAndAggregateIdOrderById(OutboxAggregateType aggregateType, Long aggregateId);

}
