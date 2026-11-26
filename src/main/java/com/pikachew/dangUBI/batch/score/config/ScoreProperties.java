package com.pikachew.dangUBI.batch.score.config;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_DEFAULT_BASIS;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_METERS_PER_KM;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_PROPERTY_PREFIX;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.domain.score.model.ScoreWeights;

/**
 * scoreJob 설정 (docs/score-spec.md 8·9장).
 *
 * 기준 거리는 Job 파라미터가 아니라 설정으로 고른다. scoreJob은 매일 새 실행을 만들려고 RunIdIncrementer를 쓰는데,
 * Spring Batch 6은 incrementer가 있는 Job에 넘긴 파라미터를 버리기 때문이다.
 * 테스트 실행: --ubi.batch.score.basis=test
 *
 * @param basis        쓸 기준 이름. 없으면 운영 기준(standard). bases에 없는 이름이면 앱이 뜨지 않는다
 * @param chunkSize    chunk 하나에 담는 trip 수(지표 Step)와 차량 수(리포트 Step)
 * @param maneuverGap  같은 기동으로 묶는 microtrip 간격
 * @param settleDelay  trip이 마지막으로 적재된 뒤 이만큼 지나야 점수 지표를 만든다. 늦게 적재된 microtrip을 놓치지 않으려는 대기 시간
 * @param weights      이벤트 묶음별 가중치
 * @param nightRates   KST 시간대(0–23) → 야간 추가 감점률. 없는 시간대는 0
 * @param bases        기준 이름 → 구간 거리·반영 범위
 */
@ConfigurationProperties(SCORE_PROPERTY_PREFIX)
public record ScoreProperties(
		String basis,
		int chunkSize,
		Duration maneuverGap,
		Duration settleDelay,
		ScoreWeights weights,
		Map<Integer, Double> nightRates,
		Map<String, Basis> bases
) {

	public ScoreProperties {
		basis = basis == null ? SCORE_DEFAULT_BASIS : basis;
		if (bases == null || !bases.containsKey(basis)) {
			throw new GeneralException(ErrorStatus.SCORE_UNKNOWN_BASIS);
		}
	}

	/** 지금 쓰는 기준의 구간 거리·반영 범위 */
	public Basis currentBasis() {
		return bases.get(basis);
	}

	/**
	 * @param segmentDistanceKm 구간 하나를 닫는 누적 거리
	 * @param windowDistanceKm  반영 감점에 넣는 최근 구간 거리
	 */
	public record Basis(double segmentDistanceKm, double windowDistanceKm) {

		public long segmentDistanceM() {
			return Math.round(segmentDistanceKm * SCORE_METERS_PER_KM);
		}

		public long windowDistanceM() {
			return Math.round(windowDistanceKm * SCORE_METERS_PER_KM);
		}

	}

}
