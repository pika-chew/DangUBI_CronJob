package com.pikachew.dangUBI.domain.outbox.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 단위 테스트: Outbox 생성 규칙. */
class OutboxTest {

	@Test
	void 처음_만들면_발행_대기_상태다() {
		Outbox outbox = Outbox.builder()
				.aggregateType(OutboxAggregateType.TRIP)
				.aggregateId(1L)
				.eventType(OutboxEventType.TRIP_STARTED)
				.payload("{\"tripId\":1}")
				.build();

		assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(outbox.getPublishedAt()).isNull();
	}

}
