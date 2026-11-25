package com.pikachew.dangUBI.domain.trip.model;

import java.time.Instant;

/**
 * ty=3 Trip 메시지. 운행이 끝난 뒤 한 번 온다. 아직 저장되지 않은 값이다.
 *
 * Lambda가 보내는 요약 필드(stt, edt, dis, hsts, mesp, dtvt)만 읽는다.
 * 실제 데이터에서 빠질 수 있어 tid를 뺀 나머지는 null을 허용한다. 측정값은 원본 Int 그대로 두고 단위 변환은 하지 않는다.
 */
public record TripSnapshot(
		String vehicleId,           // VIN
		String idempotencyKey,
		Instant receivedAt,
		int tid,
		Instant startedAt,          // stt (UTC)
		Instant endedAt,            // edt (UTC)
		Integer distance,           // dis: 운행 한 번의 누적 주행거리 (m)
		Integer maxSpeed,           // hsts (km/h)
		Integer avgSpeed,           // mesp (km/h)
		Integer drivingTime         // dtvt (초)
) implements TelemetrySnapshot {
}
