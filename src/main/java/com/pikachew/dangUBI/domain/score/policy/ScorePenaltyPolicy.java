package com.pikachew.dangUBI.domain.score.policy;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_MAX;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_METERS_PER_KM;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_MIN;

import java.util.Map;
import java.util.OptionalDouble;

import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.model.DrivingEventCounts;
import com.pikachew.dangUBI.domain.score.model.DrivingEventResult;
import com.pikachew.dangUBI.domain.score.model.ScoreWeights;

/**
 * 감점과 점수 계산 (docs/score-spec.md 5·6장).
 *
 * 감점 P = Σ w_i × X_i / D  +  Σ w_i × (Σ_h r_h × X_{i,h}) / D
 * 여러 구간의 거리 가중 평균 Σ(P_k × D_k) / Σ D_k는 집계값을 모두 더한 뒤 계산한 감점과 같다.
 * 그래서 반영 감점은 범위 안 구간의 집계값을 더해 {@link #penalty}에 넘기면 된다.
 */
public final class ScorePenaltyPolicy {

	private ScorePenaltyPolicy() {
	}

	/**
	 * trip 하나의 판정 결과를 집계값으로 바꾼다. 야간분은 시간대별 야간 추가 감점률을 곱해 더한다.
	 *
	 * @param nightRates KST 시간대(0–23) → 야간 추가 감점률. 없는 시간대는 0이다
	 */
	public static ScoreTally tally(DrivingEventResult result, long distanceM, Map<Integer, Double> nightRates) {
		DrivingEventCounts total = result.total();
		double nightOverspeed = 0;
		double nightAccel = 0;
		double nightBrake = 0;
		double nightTurn = 0;
		for (Map.Entry<Integer, DrivingEventCounts> entry : result.countsByKstHour().entrySet()) {
			double rate = nightRates.getOrDefault(entry.getKey(), 0.0);
			DrivingEventCounts counts = entry.getValue();
			nightOverspeed += rate * counts.overspeedKm();
			nightAccel += rate * counts.accelCount();
			nightBrake += rate * counts.brakeCount();
			nightTurn += rate * counts.turnCount();
		}
		return ScoreTally.builder()
				.distanceM(distanceM)
				.overspeedKm(total.overspeedKm())
				.accelCount(total.accelCount())
				.brakeCount(total.brakeCount())
				.turnCount(total.turnCount())
				.weightedNightOverspeedKm(nightOverspeed)
				.weightedNightAccel(nightAccel)
				.weightedNightBrake(nightBrake)
				.weightedNightTurn(nightTurn)
				.build();
	}

	/** 평가거리가 0이면 감점을 낼 수 없어 비어 있다 */
	public static OptionalDouble penalty(ScoreTally tally, ScoreWeights weights) {
		if (tally.getDistanceM() <= 0) {
			return OptionalDouble.empty();
		}
		double distanceKm = tally.getDistanceM() / SCORE_METERS_PER_KM;
		double base = weights.overspeed() * tally.getOverspeedKm()
				+ weights.accel() * tally.getAccelCount()
				+ weights.brake() * tally.getBrakeCount()
				+ weights.turn() * tally.getTurnCount();
		double night = weights.overspeed() * tally.getWeightedNightOverspeedKm()
				+ weights.accel() * tally.getWeightedNightAccel()
				+ weights.brake() * tally.getWeightedNightBrake()
				+ weights.turn() * tally.getWeightedNightTurn();
		return OptionalDouble.of((base + night) / distanceKm);
	}

	/** 점수 = 100 − 감점. 0–100 밖으로 나가지 않는다 */
	public static double score(double penalty) {
		return Math.min(SCORE_MAX, Math.max(SCORE_MIN, SCORE_MAX - penalty));
	}

}
