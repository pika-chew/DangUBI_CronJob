package com.pikachew.dangUBI.domain.outbox.model;

import java.util.List;

/**
 * 차량 하루 리포트 이벤트의 outbox payload. 받는 쪽(query server)이 DB를 조회하지 않고 이벤트만으로 처리하도록 값까지 담는다.
 * 숫자는 소수 둘째 자리로 반올림한다. 반올림 전 값은 vehicle_score_report에 남는다.
 *
 * @param vehicleId    VIN
 * @param reportDate   리포트 날짜 (KST, yyyy-MM-dd)
 * @param score        점수. 닫힌 구간이 없으면 null(미산출)
 * @param penalty      반영 감점. 닫힌 구간이 없으면 null
 * @param scoreChange  직전 리포트 대비 점수 변화. 직전 리포트가 없거나 어느 한쪽이 미산출이면 null
 * @param recentScores 반영 범위 안 구간별 점수 (오래된 순)
 * @param today        이번 리포트에 담긴 trip의 카운트
 * @param window       반영 범위 안 구간의 카운트 합
 */
public record VehicleScoreReportOutboxPayload(
		Long reportId,
		String vehicleId,
		String reportDate,
		Double score,
		Double penalty,
		Double scoreChange,
		List<Double> recentScores,
		Counts today,
		Counts window
) {

	/**
	 * @param distanceKm  주행거리 (km)
	 * @param overspeedKm 과속 표시 구간 거리 (km)
	 */
	public record Counts(
			double distanceKm,
			double overspeedKm,
			int accel,
			int brake,
			int turn
	) {
	}

}
