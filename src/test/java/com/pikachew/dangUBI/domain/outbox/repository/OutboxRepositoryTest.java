package com.pikachew.dangUBI.domain.outbox.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxStatus;

/** DB 통합 테스트: 운영과 같은 DDL로 만든 outbox 테이블에 저장·조회한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxRepositoryTest {

	@Autowired
	private OutboxRepository outboxRepository;

	@Test
	void 발행_대기_상태로_저장되고_기록_순서대로_조회된다() {
		outboxRepository.saveAndFlush(outbox(1L, OutboxEventType.TRIP_STARTED));
		outboxRepository.saveAndFlush(outbox(1L, OutboxEventType.TRIP_COMPLETED));
		outboxRepository.saveAndFlush(outbox(2L, OutboxEventType.TRIP_STARTED));

		assertThat(outboxRepository.findAllByAggregateTypeAndAggregateIdOrderById(OutboxAggregateType.TRIP, 1L))
				.extracting(Outbox::getEventType, Outbox::getStatus)
				.containsExactly(
						tuple(OutboxEventType.TRIP_STARTED, OutboxStatus.PENDING),
						tuple(OutboxEventType.TRIP_COMPLETED, OutboxStatus.PENDING));
	}

	@Test
	void 생성_수정_시각이_채워진다() {
		Outbox saved = outboxRepository.saveAndFlush(outbox(1L, OutboxEventType.TRIP_STARTED));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void 같은_대상의_같은_이벤트는_두_번_저장되지_않는다() {
		outboxRepository.saveAndFlush(outbox(1L, OutboxEventType.TRIP_COMPLETED));

		assertThatThrownBy(() -> outboxRepository.saveAndFlush(outbox(1L, OutboxEventType.TRIP_COMPLETED)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private static Outbox outbox(Long aggregateId, OutboxEventType eventType) {
		return Outbox.builder()
				.aggregateType(OutboxAggregateType.TRIP)
				.aggregateId(aggregateId)
				.eventType(eventType)
				.payload("{\"tripId\":" + aggregateId + "}")
				.build();
	}

}
