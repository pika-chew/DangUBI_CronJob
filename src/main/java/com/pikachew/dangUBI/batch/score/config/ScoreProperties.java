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
 * scoreJob 설정 (docs/score-spec.md 9장).
 *
 * @param chunkSize    chunk 하나에 담는 trip 수(지표 Step)와 운전자 수(리포트 Step)
 * @param maneuverGap  같은 기동으로 묶는 microtrip 간격
 * @param weights      이벤트 묶음별 가중치
 * @param nightRates   KST 시간대(0–23) → 야간 추가 감점률. 없는 시간대는 0
 * @param bases        기준 이름 → 구간 거리·반영 범위. Job 파라미터 scoreBasis로 고른다
 */
@ConfigurationProperties(SCORE_PROPERTY_PREFIX)
public record ScoreProperties(
		int chunkSize,
		Duration maneuverGap,
		ScoreWeights weights,
		Map<Integer, Double> nightRates,
		Map<String, Basis> bases
) {

	/** 이름이 없으면 운영 기준(standard)을 쓴다 */
	public Basis basis(String name) {
		Basis basis = bases.get(name == null ? SCORE_DEFAULT_BASIS : name);
		if (basis == null) {
			throw new GeneralException(ErrorStatus.SCORE_UNKNOWN_BASIS);
		}
		return basis;
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
