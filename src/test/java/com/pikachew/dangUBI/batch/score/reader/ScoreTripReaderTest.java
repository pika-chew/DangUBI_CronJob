package com.pikachew.dangUBI.batch.score.reader;

import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.batch.score.writer.TripScoreMetricWriter;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

/** DB 통합 테스트: 지표가 없는 COMPLETED trip만 id 순서로 페이지를 넘기며 읽고, Writer가 지표를 저장한다 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScoreTripReaderTest {

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripScoreMetricWriter writer;

	@Test
	void 지표가_없는_COMPLETED_trip만_페이지를_넘기며_id_순서로_읽는다() {
		Driver driver = driverRepository.save(Driver.builder().vehicleId("0DS614RB2TT925001").build());
		Trip first = completed(driver, 1);
		tripRepository.save(Trip.builder().driver(driver).vehicleId(driver.getVehicleId()).tid(2).build());
		Trip scored = completed(driver, 3);
		metricRepository.save(metric(scored));
		Trip second = completed(driver, 4);
		Trip third = completed(driver, 5);
		tripRepository.flush();

		ScoreTripReader reader = new ScoreTripReader(metricRepository, 2);

		assertThat(readAll(reader)).containsExactly(first.getId(), second.getId(), third.getId());
	}

	@Test
	void Writer가_저장한_trip은_다음_실행에서_읽지_않는다() {
		Driver driver = driverRepository.save(Driver.builder().vehicleId("0DS614RB2TT925001").build());
		Trip trip = completed(driver, 1);

		writer.write(new Chunk<>(List.of(metric(trip))));
		metricRepository.flush();

		assertThat(metricRepository.count()).isEqualTo(1);
		assertThat(readAll(new ScoreTripReader(metricRepository, 10))).isEmpty();
	}

	private Trip completed(Driver driver, int tid) {
		Trip trip = Trip.builder().driver(driver).vehicleId(driver.getVehicleId()).tid(tid).build();
		trip.complete(trip(driver.getVehicleId(), tid, "2026-11-25T00:00:00Z", "2026-11-25T00:20:00Z", 12_000));
		return tripRepository.save(trip);
	}

	private static TripScoreMetric metric(Trip trip) {
		return TripScoreMetric.builder()
				.trip(trip)
				.driver(trip.getDriver())
				.endedAt(Instant.parse("2026-11-25T00:20:00Z"))
				.tally(ScoreTally.builder().distanceM(12_000).build())
				.build();
	}

	private static List<Long> readAll(ScoreTripReader reader) {
		List<Long> ids = new ArrayList<>();
		for (Long id = reader.read(); id != null; id = reader.read()) {
			ids.add(id);
		}
		return ids;
	}

}
