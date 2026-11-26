package com.pikachew.dangUBI.domain.outbox.entity;

import java.time.Instant;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적재와 같은 트랜잭션에서 남기는 발행 대기 이벤트. 적재가 롤백되면 이벤트도 같이 사라진다.
 * 같은 대상(aggregateType, aggregateId)의 같은 eventType은 한 번만 남는다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_outbox_aggregate_event",
		columnNames = {"aggregate_type", "aggregate_id", "event_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Outbox extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OutboxAggregateType aggregateType;

	@Column(nullable = false)
	private Long aggregateId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private OutboxEventType eventType;

	/** 이벤트 내용 JSON. trip 이벤트는 식별자만 담고, 점수 리포트 이벤트는 받는 쪽이 DB를 조회하지 않도록 값까지 담는다 */
	@Column(nullable = false, length = 1024)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private OutboxStatus status;

	private Instant publishedAt;

	/** 새 이벤트는 발행 대기 상태로 만든다 */
	@Builder
	private Outbox(OutboxAggregateType aggregateType, Long aggregateId, OutboxEventType eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = OutboxStatus.PENDING;
	}

}
