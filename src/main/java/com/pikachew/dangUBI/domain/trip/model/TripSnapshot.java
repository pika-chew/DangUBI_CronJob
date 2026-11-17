package com.pikachew.dangUBI.domain.trip.model;

import java.time.Instant;

/**
 * ty=3 Trip 메시지. 운행이 끝난 뒤 한 번 온다. 아직 저장되지 않은 값이다.
 *
 * 문서상 필수(M) 필드도 실제 데이터에서 빠질 수 있어 tid를 뺀 나머지는 null을 허용한다.
 * 측정값은 원본 Int 그대로 두고 단위·스케일 변환은 하지 않는다. usm(휴대폰 번호)은 개인정보라 읽지 않는다.
 */
public record TripSnapshot(
		String vehicleId,
		String idempotencyKey,
		Instant receivedAt,
		int tid,
		Instant startedAt,          // stt (UTC)
		Instant endedAt,            // edt (UTC)
		Integer distance,           // dis (m)
		Integer totalDistance,      // tdis (m)
		Integer fuelConsumption,    // fc
		Integer startLat,           // stlat
		Integer startLon,           // stlon
		Integer endLat,             // edlat
		Integer endLon,             // edlon
		Integer coolantTempAvg,     // ctp
		Integer carbonEmission,     // coe
		Integer fuelCutTime,        // fct
		Integer maxSpeed,           // hsts
		Integer avgSpeed,           // mesp
		Integer idleTime,           // idt
		Integer batteryVoltage,     // btv
		Integer generatorVoltage,   // gnv
		Integer warmupTime,         // wut
		Integer highSpeedTime,      // est
		Integer firmwareVersion,    // fwv
		Integer drivingTime         // dtvt
) implements TelemetrySnapshot {
}
