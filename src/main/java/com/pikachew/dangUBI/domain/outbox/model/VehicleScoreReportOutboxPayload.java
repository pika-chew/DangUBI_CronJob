package com.pikachew.dangUBI.domain.outbox.model;

/**
 * 차량 하루 리포트 이벤트의 outbox payload. 식별자만 담고, 값은 소비자가 vehicle_score_report 테이블에서 읽는다.
 *
 * @param vehicleId  VIN
 * @param reportDate 리포트 날짜 (KST, yyyy-MM-dd)
 */
public record VehicleScoreReportOutboxPayload(
		Long reportId,
		String vehicleId,
		String reportDate
) {
}
