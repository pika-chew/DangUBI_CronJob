package com.pikachew.dangUBI.domain.score.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.model.DrivingEventCounts;
import com.pikachew.dangUBI.domain.score.model.DrivingEventResult;
import com.pikachew.dangUBI.domain.score.model.ScoreWeights;

/** 단위 테스트: 감점 공식, 야간 가중, 점수 범위 (docs/score-spec.md 5·6장 예시) */
class ScorePenaltyPolicyTest {

	private static final ScoreWeights DANGUBI = new ScoreWeights(120, 90, 200, 100);
	private static final Map<Integer, Double> NIGHT_RATES = Map.of(23, 0.2539, 0, 0.2692, 1, 0.40, 2, 0.384, 3, 0.164);

	@Test
	void 구간_125km_예시는_감점_13_84점이다() {
		// 과속 2.5km, 급가속 5회, 급감속 4회(그중 1회는 01–02시), 급회전 1회
		DrivingEventResult result = new DrivingEventResult(Map.of(
				10, new DrivingEventCounts(2.5, 5, 3, 1),
				1, new DrivingEventCounts(0, 0, 1, 0)), 0);

		ScoreTally tally = ScorePenaltyPolicy.tally(result, 125_000, NIGHT_RATES);
		double penalty = ScorePenaltyPolicy.penalty(tally, DANGUBI).orElseThrow();

		assertThat(penalty).isCloseTo(13.84, within(1e-9));
		assertThat(ScorePenaltyPolicy.score(penalty)).isCloseTo(86.16, within(1e-9));
	}

	@Test
	void Notion_100km_과속_예시는_97점이다() {
		ScoreTally tally = ScoreTally.builder().distanceM(100_000).overspeedKm(3).build();

		double penalty = ScorePenaltyPolicy.penalty(tally, new ScoreWeights(100, 100, 200, 150)).orElseThrow();

		assertThat(ScorePenaltyPolicy.score(penalty)).isCloseTo(97, within(1e-9));
	}

	@Test
	void 야간_시간대는_감점률을_곱해_더하고_주간은_0이다() {
		DrivingEventResult result = new DrivingEventResult(Map.of(
				2, new DrivingEventCounts(1.0, 1, 0, 0),
				14, new DrivingEventCounts(5.0, 3, 0, 0)), 0);

		ScoreTally tally = ScorePenaltyPolicy.tally(result, 50_000, NIGHT_RATES);

		assertThat(tally.getOverspeedKm()).isCloseTo(6.0, within(1e-9));
		assertThat(tally.getAccelCount()).isEqualTo(4);
		assertThat(tally.getWeightedNightOverspeedKm()).isCloseTo(0.384, within(1e-9));
		assertThat(tally.getWeightedNightAccel()).isCloseTo(0.384, within(1e-9));
	}

	@Test
	void 평가거리가_0이면_감점을_내지_않는다() {
		assertThat(ScorePenaltyPolicy.penalty(ScoreTally.builder().accelCount(3).build(), DANGUBI)).isEmpty();
	}

	@Test
	void 점수는_0과_100_사이로_자른다() {
		assertThat(ScorePenaltyPolicy.score(130)).isZero();
		assertThat(ScorePenaltyPolicy.score(0)).isEqualTo(100);
	}

	@Test
	void 구간을_합친_감점은_구간_감점의_거리_가중_평균과_같다() {
		ScoreTally first = ScoreTally.builder().distanceM(125_000).brakeCount(1).build();
		ScoreTally second = ScoreTally.builder().distanceM(250_000).brakeCount(5).build();
		double p1 = ScorePenaltyPolicy.penalty(first, DANGUBI).orElseThrow();
		double p2 = ScorePenaltyPolicy.penalty(second, DANGUBI).orElseThrow();

		double pooled = ScorePenaltyPolicy.penalty(first.plus(second), DANGUBI).orElseThrow();

		assertThat(pooled).isCloseTo((p1 * 125 + p2 * 250) / 375, within(1e-9));
	}

}
