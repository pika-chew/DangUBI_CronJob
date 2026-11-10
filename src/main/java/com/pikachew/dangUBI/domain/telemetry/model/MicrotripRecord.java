package com.pikachew.dangUBI.domain.telemetry.model;

import java.time.Instant;

/**
 * ty=4 Microtrip 메시지. 운행 중 주기적으로 온다.
 *
 * tid와 collectedAt(clt)을 뺀 나머지는 null을 허용한다. 측정값은 원본 Int 그대로 둔다.
 */
public record MicrotripRecord(
		String vehicleId,
		String idempotencyKey,
		Instant receivedAt,
		int tid,
		Instant collectedAt,            // clt (단말기 기준 수집 시각)
		Integer lat,                    // lat (WGS84, 스케일 미확인)
		Integer lon,                    // lon
		Integer locationAccuracy,       // lc
		Integer fuelConsumption,        // fc
		Integer tripDistance,           // cdit
		Integer rpm,                    // rpm
		Integer speed,                  // sp
		Integer eventMask,              // em: 16진 문자열을 푼 비트마스크. bit0 급출발 … bit6 급정지
		Integer engineLoad,             // el
		Integer accX,                   // xyz[0]
		Integer accY,                   // xyz[1]
		Integer gyroY,                  // xyz[2]
		Integer batteryVoltage,         // vv
		Integer accelPedalPosition      // tpos
) implements TelemetryRecord {
}
