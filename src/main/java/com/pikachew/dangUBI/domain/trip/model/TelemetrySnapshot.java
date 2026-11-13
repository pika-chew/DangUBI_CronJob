package com.pikachew.dangUBI.domain.trip.model;

import java.time.Instant;

/**
 * 수집 파일에서 읽은 메시지 한 건. 아직 저장되지 않은 값이다.
 * ty=3은 {@link TripSnapshot}, ty=4는 {@link MicrotripSnapshot}이다.
 */
public sealed interface TelemetrySnapshot permits TripSnapshot, MicrotripSnapshot {

	String vehicleId();

	String idempotencyKey();

	/** envelope의 ts. Lambda가 받은 시각으로 보인다. 없으면 null */
	Instant receivedAt();

	int tid();

}
