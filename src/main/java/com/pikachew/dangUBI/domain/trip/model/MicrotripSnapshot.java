package com.pikachew.dangUBI.domain.trip.model;

import java.time.Instant;

/**
 * ty=4 Microtrip 메시지. 운행 중 주기적으로 온다. 아직 저장되지 않은 값이다.
 *
 * 점수 계산에 쓰는 필드만 읽는다. tid와 collectedAt(clt)을 뺀 나머지는 null을 허용한다.
 */
public record MicrotripSnapshot(
		String vehicleId,
		String idempotencyKey,
		Instant receivedAt,
		int tid,
		Instant collectedAt,            // clt (단말기 기준 수집 시각)
		Integer speed,                  // sp (km/h)
		Integer eventMask               // em: 16진 문자열을 푼 비트마스크. bit0 급출발 … bit6 급정지, bit7 과속
) implements TelemetrySnapshot {
}
