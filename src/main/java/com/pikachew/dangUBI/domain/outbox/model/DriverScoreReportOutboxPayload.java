package com.pikachew.dangUBI.domain.outbox.model;

/**
 * 하루 리포트 이벤트의 outbox payload. 식별자만 담고, 값은 소비자가 driver_score_report 테이블에서 읽는다.
 *
 * @param reportDate 리포트 날짜 (KST, yyyy-MM-dd)
 */
public record DriverScoreReportOutboxPayload(
		Long reportId,
		Long driverId,
		String vehicleId,
		String reportDate
) {
}
