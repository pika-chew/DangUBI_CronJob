package com.pikachew.dangUBI.domain.score.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 감점 계산에 들어가는 거리와 이벤트 집계값. trip 지표와 구간이 같은 컬럼 묶음으로 쓴다.
 *
 * weightedNight*는 야간 발생분에 시간대별 야간 추가 감점률을 곱해 더한 값이다 (Σ r_h × 그 시간대 발생분).
 * 그래서 야간 감점 = Σ w_i × weightedNight_i / D로 바로 계산된다.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreTally {

	/** 평가 주행거리 D (m). 끝 글자 하나만 대문자인 이름은 기본 규칙으로 distancem이 되므로 컬럼 이름을 적는다 */
	@Column(name = "distance_m", nullable = false)
	private long distanceM;

	@Column(nullable = false)
	private double overspeedKm;
	@Column(nullable = false)
	private int accelCount;
	@Column(nullable = false)
	private int brakeCount;
	@Column(nullable = false)
	private int turnCount;

	@Column(nullable = false)
	private double weightedNightOverspeedKm;
	@Column(nullable = false)
	private double weightedNightAccel;
	@Column(nullable = false)
	private double weightedNightBrake;
	@Column(nullable = false)
	private double weightedNightTurn;

	@Builder
	private ScoreTally(long distanceM, double overspeedKm, int accelCount, int brakeCount, int turnCount,
			double weightedNightOverspeedKm, double weightedNightAccel, double weightedNightBrake, double weightedNightTurn) {
		this.distanceM = distanceM;
		this.overspeedKm = overspeedKm;
		this.accelCount = accelCount;
		this.brakeCount = brakeCount;
		this.turnCount = turnCount;
		this.weightedNightOverspeedKm = weightedNightOverspeedKm;
		this.weightedNightAccel = weightedNightAccel;
		this.weightedNightBrake = weightedNightBrake;
		this.weightedNightTurn = weightedNightTurn;
	}

	public ScoreTally plus(ScoreTally other) {
		return ScoreTally.builder()
				.distanceM(distanceM + other.distanceM)
				.overspeedKm(overspeedKm + other.overspeedKm)
				.accelCount(accelCount + other.accelCount)
				.brakeCount(brakeCount + other.brakeCount)
				.turnCount(turnCount + other.turnCount)
				.weightedNightOverspeedKm(weightedNightOverspeedKm + other.weightedNightOverspeedKm)
				.weightedNightAccel(weightedNightAccel + other.weightedNightAccel)
				.weightedNightBrake(weightedNightBrake + other.weightedNightBrake)
				.weightedNightTurn(weightedNightTurn + other.weightedNightTurn)
				.build();
	}

}
