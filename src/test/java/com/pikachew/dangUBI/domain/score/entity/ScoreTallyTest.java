package com.pikachew.dangUBI.domain.score.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 단위 테스트: 집계값 더하기 */
class ScoreTallyTest {

	@Test
	void 아무_값도_주지_않으면_모두_0이다() {
		ScoreTally empty = ScoreTally.builder().build();

		assertThat(empty.getDistanceM()).isZero();
		assertThat(empty.getOverspeedKm()).isZero();
		assertThat(empty.getWeightedNightTurn()).isZero();
	}

	@Test
	void 항목별로_더한다() {
		ScoreTally a = ScoreTally.builder().distanceM(1000).overspeedKm(0.5).accelCount(1).brakeCount(2).turnCount(3)
				.weightedNightOverspeedKm(0.1).weightedNightAccel(0.25).weightedNightBrake(0.4).weightedNightTurn(0.16).build();
		ScoreTally b = ScoreTally.builder().distanceM(2000).overspeedKm(1.0).accelCount(4).brakeCount(5).turnCount(6)
				.weightedNightOverspeedKm(0.2).weightedNightAccel(0.25).weightedNightBrake(0.4).weightedNightTurn(0.16).build();

		ScoreTally sum = a.plus(b);

		assertThat(sum).isEqualTo(ScoreTally.builder().distanceM(3000).overspeedKm(1.5).accelCount(5).brakeCount(7).turnCount(9)
				.weightedNightOverspeedKm(0.1 + 0.2).weightedNightAccel(0.5).weightedNightBrake(0.8).weightedNightTurn(0.32).build());
	}

}
