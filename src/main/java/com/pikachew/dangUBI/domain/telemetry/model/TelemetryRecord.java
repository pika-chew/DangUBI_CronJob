package com.pikachew.dangUBI.domain.telemetry.model;

import java.time.Instant;

/** gz JSONL 한 줄을 해석한 결과. ty=3은 {@link TripRecord}, ty=4는 {@link MicrotripRecord}다. */
public sealed interface TelemetryRecord permits TripRecord, MicrotripRecord {

	String vehicleId();

	String idempotencyKey();

	/** envelope의 ts. Lambda가 받은 시각으로 보인다. 없으면 null. */
	Instant receivedAt();

	int tid();

}
