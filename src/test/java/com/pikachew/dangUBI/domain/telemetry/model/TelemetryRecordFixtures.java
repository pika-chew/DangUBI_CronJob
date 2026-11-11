package com.pikachew.dangUBI.domain.telemetry.model;

import java.time.Instant;

/** 테스트용 레코드. 필요한 값만 받고 나머지는 null로 둔다. */
public final class TelemetryRecordFixtures {

	private TelemetryRecordFixtures() {
	}

	public static TripRecord trip(String vehicleId, int tid, String startedAt, String endedAt, int distance) {
		return new TripRecord(vehicleId, "trip-" + vehicleId + "-" + tid, Instant.parse(endedAt), tid,
				Instant.parse(startedAt), Instant.parse(endedAt), distance,
				null, null, null, null, null, null, null, null, null, 90, 30, null, null, null, null, null, null, 3600);
	}

	public static MicrotripRecord microtrip(String vehicleId, int tid, String idempotencyKey, String collectedAt) {
		return new MicrotripRecord(vehicleId, idempotencyKey, Instant.parse(collectedAt), tid, Instant.parse(collectedAt),
				null, null, null, null, null, null, 70, 0x28, null, null, null, null, null, null);
	}

}
