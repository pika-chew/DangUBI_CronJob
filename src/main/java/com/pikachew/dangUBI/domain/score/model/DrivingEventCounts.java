package com.pikachew.dangUBI.domain.score.model;

/**
 * 이벤트 묶음별 집계값.
 *
 * @param overspeedKm 과속 표시 구간 거리 (km)
 * @param accelCount  급가속(급출발 포함) 횟수
 * @param brakeCount  급감속(급정지 포함) 횟수
 * @param turnCount   급회전(좌·우회전, 유턴) 횟수
 */
public record DrivingEventCounts(
		double overspeedKm,
		int accelCount,
		int brakeCount,
		int turnCount
) {

	public DrivingEventCounts plus(DrivingEventCounts other) {
		return new DrivingEventCounts(
				overspeedKm + other.overspeedKm,
				accelCount + other.accelCount,
				brakeCount + other.brakeCount,
				turnCount + other.turnCount);
	}

}
