package com.pikachew.dangUBI.domain.trip.service;

import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.microtrip;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.entity.TripStatus;
import com.pikachew.dangUBI.domain.trip.model.TripIngestResult;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 통합 테스트: H2에 운영과 같은 DDL로 테이블을 만들고 적재 결과를 확인한다.
 * ingest 호출 사이에 flush·clear로 영속성 컨텍스트를 비워, 서로 다른 chunk에서 들어온 것처럼 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripDomainServiceImplTest {

	private static final String V1 = "vehicle-001";
	private static final String TRIP_START = "2026-11-10T10:00:00Z";
	private static final String TRIP_END = "2026-11-10T11:00:00Z";

	@Autowired
	private TripDomainService tripDomainService;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripMicrotripRepository microtripRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void microtrip만_오면_진행_중인_trip과_운전자를_만든다() {
		ingest(List.of(), List.of(
				microtrip(V1, 354, "m1", "2026-11-10T10:30:00Z"),
				microtrip(V1, 354, "m2", "2026-11-10T10:00:00Z")));

		Trip trip = findTrip(V1, 354);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.IN_PROGRESS);
		assertThat(trip.getFirstCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
		assertThat(trip.getLastCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:30:00Z"));
		assertThat(trip.getDriver().getVehicleId()).isEqualTo(V1);
		assertThat(microtripRepository.findByTripOrderByCollectedAt(trip))
				.extracting(TripMicrotrip::getIdempotencyKey).containsExactly("m2", "m1");
	}

	@Test
	void ty3만_오면_종료된_trip을_만든다() {
		ingest(List.of(trip(V1, 354, TRIP_START, TRIP_END, 30000)), List.of());

		Trip trip = findTrip(V1, 354);
		assertThat(trip.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(trip.getDistance()).isEqualTo(30000);
		assertThat(trip.getFirstCollectedAt()).isNull();
	}

	@Test
	void microtrip과_ty3_중_어느_쪽이_먼저_와도_결과가_같다() {
		// 경우 A: microtrip → ty3
		ingest(List.of(), List.of(microtrip("vehicle-A", 1, "a1", "2026-11-10T10:30:00Z")));
		ingest(List.of(), List.of(microtrip("vehicle-A", 1, "a2", "2026-11-10T10:00:00Z")));
		ingest(List.of(trip("vehicle-A", 1, TRIP_START, TRIP_END, 30000)), List.of());
		// 경우 B: ty3 → microtrip
		ingest(List.of(trip("vehicle-B", 1, TRIP_START, TRIP_END, 30000)), List.of());
		ingest(List.of(), List.of(microtrip("vehicle-B", 1, "b2", "2026-11-10T10:00:00Z")));
		ingest(List.of(), List.of(microtrip("vehicle-B", 1, "b1", "2026-11-10T10:30:00Z")));

		Trip a = findTrip("vehicle-A", 1);
		Trip b = findTrip("vehicle-B", 1);
		assertThat(a.getStatus()).isEqualTo(b.getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(a.getStartedAt()).isEqualTo(b.getStartedAt());
		assertThat(a.getEndedAt()).isEqualTo(b.getEndedAt());
		assertThat(a.getDistance()).isEqualTo(b.getDistance());
		assertThat(a.getFirstCollectedAt()).isEqualTo(b.getFirstCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
		assertThat(a.getLastCollectedAt()).isEqualTo(b.getLastCollectedAt()).isEqualTo(Instant.parse("2026-11-10T10:30:00Z"));
		assertThat(microtripRepository.findByTripOrderByCollectedAt(a)).hasSize(2);
		assertThat(microtripRepository.findByTripOrderByCollectedAt(b)).hasSize(2);
	}

	@Test
	void 같은_데이터를_다시_넣어도_결과가_같다() {
		List<TripSnapshot> trips = List.of(trip(V1, 354, TRIP_START, TRIP_END, 30000));
		List<MicrotripSnapshot> microtrips = List.of(
				microtrip(V1, 354, "m1", "2026-11-10T10:00:00Z"),
				microtrip(V1, 354, "m2", "2026-11-10T10:30:00Z"));

		TripIngestResult first = ingest(trips, microtrips);
		TripIngestResult second = ingest(trips, microtrips);

		assertThat(first.insertedMicrotrips()).isEqualTo(2);
		assertThat(second.insertedMicrotrips()).isZero();
		assertThat(second.duplicateMicrotrips()).isEqualTo(2);
		assertThat(tripRepository.count()).isEqualTo(1);
		assertThat(microtripRepository.count()).isEqualTo(2);
		assertThat(findTrip(V1, 354).getStatus()).isEqualTo(TripStatus.COMPLETED);
	}

	@Test
	void 한_번의_입력_안에서_겹치는_microtrip은_하나만_저장한다() {
		MicrotripSnapshot record = microtrip(V1, 354, "m1", "2026-11-10T10:00:00Z");

		TripIngestResult result = ingest(List.of(), List.of(record, record));

		assertThat(result.insertedMicrotrips()).isEqualTo(1);
		assertThat(result.duplicateMicrotrips()).isEqualTo(1);
		assertThat(microtripRepository.count()).isEqualTo(1);
	}

	@Test
	void 같은_차량의_여러_trip은_운전자를_공유한다() {
		ingest(List.of(trip(V1, 354, TRIP_START, TRIP_END, 1)), List.of(microtrip(V1, 355, "m1", TRIP_END)));
		ingest(List.of(), List.of(microtrip(V1, 356, "m2", TRIP_END)));

		assertThat(driverRepository.count()).isEqualTo(1);
		assertThat(tripRepository.count()).isEqualTo(3);
	}

	@Test
	void tid가_같아도_차량이_다르면_다른_trip이다() {
		ingest(List.of(), List.of(
				microtrip("vehicle-001", 354, "m1", TRIP_START),
				microtrip("vehicle-002", 354, "m2", TRIP_START)));

		assertThat(tripRepository.count()).isEqualTo(2);
		assertThat(driverRepository.count()).isEqualTo(2);
	}

	@Test
	void 생성_수정_시각이_채워진다() {
		ingest(List.of(), List.of(microtrip(V1, 354, "m1", TRIP_START)));

		Trip trip = findTrip(V1, 354);
		assertThat(trip.getCreatedAt()).isNotNull();
		assertThat(trip.getUpdatedAt()).isNotNull();
	}

	@Test
	void 시각은_밀리초까지_UTC_그대로_저장된다() {
		ingest(List.of(), List.of(microtrip(V1, 354, "m1", "2026-11-10T23:59:59.123Z")));

		assertThat(findTrip(V1, 354).getLastCollectedAt()).isEqualTo(Instant.parse("2026-11-10T23:59:59.123Z"));
	}

	private TripIngestResult ingest(List<TripSnapshot> trips, List<MicrotripSnapshot> microtrips) {
		TripIngestResult result = tripDomainService.ingest(trips, microtrips);
		entityManager.flush();
		entityManager.clear();
		return result;
	}

	private Trip findTrip(String vehicleId, int tid) {
		return tripRepository.findByVehicleIdAndTid(vehicleId, tid).orElseThrow();
	}

}
