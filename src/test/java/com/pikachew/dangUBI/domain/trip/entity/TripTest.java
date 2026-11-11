package com.pikachew.dangUBI.domain.trip.entity;

import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.domain.driver.entity.Driver;

/** 단위 테스트: Trip 상태 변경 규칙. */
class TripTest {

	private final Driver driver = Driver.ofVehicle("vehicle-001");

	@Test
	void 처음_만들면_진행_중이다() {
		Trip trip = Trip.start(driver, "vehicle-001", 354);

		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
		assertThat(trip.getFirstCollectedAt()).isNull();
	}

	@Test
	void 수집_범위는_순서와_상관없이_최솟값과_최댓값이다() {
		Trip forward = Trip.start(driver, "vehicle-001", 354);
		Trip backward = Trip.start(driver, "vehicle-001", 354);
		Instant t1 = Instant.parse("2026-11-10T10:00:00Z");
		Instant t2 = Instant.parse("2026-11-10T10:10:00Z");
		Instant t3 = Instant.parse("2026-11-10T10:30:00Z");

		forward.recordCollectedAt(t1);
		forward.recordCollectedAt(t2);
		forward.recordCollectedAt(t3);
		backward.recordCollectedAt(t2);
		backward.recordCollectedAt(t3);
		backward.recordCollectedAt(t1);

		assertThat(forward.getFirstCollectedAt()).isEqualTo(t1).isEqualTo(backward.getFirstCollectedAt());
		assertThat(forward.getLastCollectedAt()).isEqualTo(t3).isEqualTo(backward.getLastCollectedAt());
	}

	@Test
	void ty3을_받으면_종료하고_요약값을_채운다() {
		Trip trip = Trip.start(driver, "vehicle-001", 354);

		trip.complete(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000));

		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getStartedAt()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
		assertThat(trip.getEndedAt()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
		assertThat(trip.getDistance()).isEqualTo(30000);
		assertThat(trip.getTripIdempotencyKey()).isEqualTo("trip-vehicle-001-354");
	}

	@Test
	void 종료된_뒤_microtrip이_와도_상태는_그대로다() {
		Trip trip = Trip.start(driver, "vehicle-001", 354);
		trip.complete(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000));

		trip.recordCollectedAt(Instant.parse("2026-11-10T10:30:00Z"));

		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getLastCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:30:00Z"));
	}

	@Test
	void ty3을_받아도_수집_범위는_건드리지_않는다() {
		Trip trip = Trip.start(driver, "vehicle-001", 354);
		trip.recordCollectedAt(Instant.parse("2026-11-10T10:05:00Z"));

		trip.complete(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000));

		assertThat(trip.getFirstCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:05:00Z"));
	}

}
