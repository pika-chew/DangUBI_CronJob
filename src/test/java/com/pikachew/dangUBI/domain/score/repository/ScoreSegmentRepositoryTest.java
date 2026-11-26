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

import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

/** DB 통합 테스트: 구간 저장, 순번 유니크, 구간에 묶이지 않은 지표 조회 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScoreSegmentRepositoryTest {

	@Autowired
	private ScoreSegmentRepository segmentRepository;

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private TripRepository tripRepository;

	@Test
	void 마지막_구간과_최근_구간_목록을_순번_역순으로_읽는다() {
		Driver driver = savedDriver();
		segmentRepository.save(segment(driver, 1));
		segmentRepository.save(segment(driver, 2));
		segmentRepository.saveAndFlush(segment(driver, 3));

		assertThat(segmentRepository.findTopByVehicleIdOrderBySequenceNoDesc(driver.getVehicleId()).orElseThrow().getSequenceNo()).isEqualTo(3);
		assertThat(segmentRepository.findTop100ByVehicleIdOrderBySequenceNoDesc(driver.getVehicleId()))
				.extracting(ScoreSegment::getSequenceNo).containsExactly(3, 2, 1);
	}

	@Test
	void 같은_차량의_같은_순번은_두_번_저장되지_않는다() {
		Driver driver = savedDriver();
		segmentRepository.saveAndFlush(segment(driver, 1));

		assertThatThrownBy(() -> segmentRepository.saveAndFlush(segment(driver, 1)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 구간에_묶이지_않은_지표만_종료_시각_순서로_읽는다() {
		Driver driver = savedDriver();
		TripScoreMetric late = metricRepository.save(metric(driver, 1, "2026-11-25T03:00:00Z"));
		TripScoreMetric early = metricRepository.save(metric(driver, 2, "2026-11-25T01:00:00Z"));
		TripScoreMetric segmented = metricRepository.save(metric(driver, 3, "2026-11-25T00:00:00Z"));
		segmented.assignSegment(segmentRepository.save(segment(driver, 1)));
		metricRepository.flush();

		assertThat(metricRepository.findByVehicleIdAndSegmentIsNullOrderByEndedAtAscIdAsc(driver.getVehicleId()))
				.containsExactly(early, late);
	}

	private Driver savedDriver() {
		return driverRepository.save(Driver.builder().vehicleId("0DS614RB2TT925001").build());
	}

	private TripScoreMetric metric(Driver driver, int tid, String endedAt) {
		Trip trip = tripRepository.save(Trip.builder().driver(driver).vehicleId(driver.getVehicleId()).tid(tid).build());
		return TripScoreMetric.builder()
				.trip(trip)
				.vehicleId(driver.getVehicleId())
				.endedAt(Instant.parse(endedAt))
				.tally(ScoreTally.builder().distanceM(10_000).build())
				.build();
	}

	private static ScoreSegment segment(Driver driver, int sequenceNo) {
		return ScoreSegment.builder()
				.vehicleId(driver.getVehicleId())
				.sequenceNo(sequenceNo)
				.tally(ScoreTally.builder().distanceM(125_000).brakeCount(2).build())
				.penalty(3.2)
				.build();
	}

}
