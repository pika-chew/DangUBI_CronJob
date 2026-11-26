package com.pikachew.dangUBI.domain.score.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

import jakarta.persistence.EntityManager;

/** DB 통합 테스트: 운영과 같은 DDL로 만든 trip_score_metric 테이블에 저장·조회한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TripScoreMetricRepositoryTest {

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 집계값을_저장하고_다시_읽는다() {
		Trip trip = savedTrip(1);
		ScoreTally tally = ScoreTally.builder().distanceM(12000).overspeedKm(0.08).accelCount(3).brakeCount(2).turnCount(1)
				.weightedNightBrake(0.4).build();

		Long id = metricRepository.saveAndFlush(metric(trip, tally)).getId();
		entityManager.clear();

		TripScoreMetric found = metricRepository.findById(id).orElseThrow();
		assertThat(found.getTally()).isEqualTo(tally);
		assertThat(found.getEndedAt()).isEqualTo(Instant.parse("2026-11-25T00:20:00Z"));
		assertThat(found.getCreatedAt()).isNotNull();
	}

	@Test
	void 같은_trip의_지표는_두_번_저장되지_않는다() {
		Trip trip = savedTrip(1);
		metricRepository.saveAndFlush(metric(trip, ScoreTally.builder().build()));

		assertThatThrownBy(() -> metricRepository.saveAndFlush(metric(trip, ScoreTally.builder().build())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Trip savedTrip(long tid) {
		String vehicleId = "0DS614RB2TT925001";
		return tripRepository.save(Trip.builder().vehicleId(vehicleId).tid(tid).build());
	}

	private static TripScoreMetric metric(Trip trip, ScoreTally tally) {
		return TripScoreMetric.builder()
				.trip(trip)
				.vehicleId(trip.getVehicleId())
				.endedAt(Instant.parse("2026-11-25T00:20:00Z"))
				.tally(tally)
				.build();
	}

}
