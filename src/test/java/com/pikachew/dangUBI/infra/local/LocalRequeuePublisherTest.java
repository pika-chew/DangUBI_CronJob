package com.pikachew.dangUBI.infra.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;

/** 단위 테스트: 로그만 남기는 로컬 재큐잉 구현체 */
class LocalRequeuePublisherTest {

	private final LocalRequeuePublisher publisher = new LocalRequeuePublisher();

	@Test
	void 받은_누락_객체_수를_그대로_재큐잉_건수로_돌려준다() {
		List<ObjectRef> missing = List.of(
				new ObjectRef("firehose/processed/2026/11/08/03/a.jsonl.gz", 1),
				new ObjectRef("firehose/processed/2026/11/08/14/b.jsonl.gz", 2));

		assertThat(publisher.publish(missing)).isEqualTo(2);
	}

	@Test
	void 빈_목록이면_0건이다() {
		assertThat(publisher.publish(List.of())).isZero();
	}

}
