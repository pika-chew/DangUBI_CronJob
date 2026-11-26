package com.pikachew.dangUBI.domain.trip.model;

import java.time.Instant;

/** 테스트용 Snapshot. 필요한 값만 받고 나머지는 null로 둔다 */
public final class TripSnapshotFixtures {

	private TripSnapshotFixtures() {
	}

	public static TripSnapshot trip(String vehicleId, long tid, String startedAt, String endedAt, int distance) {
		return new TripSnapshot(vehicleId, "trip-" + vehicleId + "-" + tid, Instant.parse(endedAt), tid,
				Instant.parse(startedAt), Instant.parse(endedAt), distance, 90, 30, 3600);
	}

	public static MicrotripSnapshot microtrip(String vehicleId, long tid, String idempotencyKey, String collectedAt) {
		return new MicrotripSnapshot(vehicleId, idempotencyKey, Instant.parse(collectedAt), tid, Instant.parse(collectedAt),
				70, 0x28);
	}

}
