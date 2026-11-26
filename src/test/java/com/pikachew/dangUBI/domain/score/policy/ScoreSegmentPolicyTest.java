package com.pikachew.dangUBI.domain.score.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;

/** 단위 테스트: 구간 닫기(trip을 자르지 않음)와 반영 범위 고르기 */
class ScoreSegmentPolicyTest {

	private static final long SEGMENT_M = 125_000;

	@Test
	void 누적이_구간_거리를_넘는_trip까지_묶어_구간을_닫는다() {
		List<TripScoreMetric> metrics = List.of(metric(60_000), metric(50_000), metric(40_000), metric(10_000));

		List<List<TripScoreMetric>> closed = ScoreSegmentPolicy.closeSegments(metrics, SEGMENT_M);

		assertThat(closed).hasSize(1);
		assertThat(closed.get(0)).containsExactly(metrics.get(0), metrics.get(1), metrics.get(2));
	}

	@Test
	void 구간_거리와_정확히_같으면_닫는다() {
		List<TripScoreMetric> metrics = List.of(metric(100_000), metric(25_000));

		assertThat(ScoreSegmentPolicy.closeSegments(metrics, SEGMENT_M)).hasSize(1);
	}

	@Test
	void 한_번에_여러_구간을_닫을_수_있다() {
		List<TripScoreMetric> metrics = List.of(metric(130_000), metric(70_000), metric(60_000), metric(5_000));

		List<List<TripScoreMetric>> closed = ScoreSegmentPolicy.closeSegments(metrics, SEGMENT_M);

		assertThat(closed).hasSize(2);
		assertThat(closed.get(0)).containsExactly(metrics.get(0));
		assertThat(closed.get(1)).containsExactly(metrics.get(1), metrics.get(2));
	}

	@Test
	void 구간_거리를_채우지_못하면_닫지_않는다() {
		assertThat(ScoreSegmentPolicy.closeSegments(List.of(metric(60_000), metric(60_000)), SEGMENT_M)).isEmpty();
	}

	@Test
	void 최근_구간부터_반영_범위를_채울_때까지_고르고_오래된_순서로_돌려준다() {
		ScoreSegment s1 = segment(1, 130_000);
		ScoreSegment s2 = segment(2, 125_000);
		ScoreSegment s3 = segment(3, 180_000);
		ScoreSegment s4 = segment(4, 126_000);

		// 300km 범위: s4(126) → s3(306, 범위를 넘기게 하는 구간까지 넣음) → 그만
		List<ScoreSegment> window = ScoreSegmentPolicy.window(List.of(s4, s3, s2, s1), 300_000);

		assertThat(window).containsExactly(s3, s4);
	}

	@Test
	void 구간이_범위보다_적으면_있는_구간을_모두_쓴다() {
		ScoreSegment s1 = segment(1, 130_000);

		assertThat(ScoreSegmentPolicy.window(List.of(s1), 1_500_000)).containsExactly(s1);
		assertThat(ScoreSegmentPolicy.window(List.of(), 1_500_000)).isEmpty();
	}

	private static TripScoreMetric metric(long distanceM) {
		return TripScoreMetric.builder()
				.endedAt(Instant.parse("2026-11-25T00:00:00Z"))
				.tally(ScoreTally.builder().distanceM(distanceM).build())
				.build();
	}

	private static ScoreSegment segment(int sequenceNo, long distanceM) {
		return ScoreSegment.builder()
				.sequenceNo(sequenceNo)
				.tally(ScoreTally.builder().distanceM(distanceM).build())
				.build();
	}

}
