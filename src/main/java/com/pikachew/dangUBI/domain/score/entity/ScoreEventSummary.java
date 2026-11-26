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
 * 하루 리포트에 싣는 거리와 이벤트 카운트. 야간 가중값은 싣지 않는다.
 * 리포트에서 "오늘"과 "반영 범위" 두 번 쓰므로 컬럼 이름은 쓰는 쪽에서 접두어를 붙인다.
 */
@Getter
@Embeddable
@EqualsAndHashCode
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreEventSummary {

	@Column(nullable = false)
	private long distanceM;
	@Column(nullable = false)
	private double overspeedKm;
	@Column(nullable = false)
	private int accelCount;
	@Column(nullable = false)
	private int brakeCount;
	@Column(nullable = false)
	private int turnCount;

	@Builder
	private ScoreEventSummary(long distanceM, double overspeedKm, int accelCount, int brakeCount, int turnCount) {
		this.distanceM = distanceM;
		this.overspeedKm = overspeedKm;
		this.accelCount = accelCount;
		this.brakeCount = brakeCount;
		this.turnCount = turnCount;
	}

}
