package com.pikachew.dangUBI.domain.score.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.domain.score.entity.VehicleScoreReport;
import com.pikachew.dangUBI.domain.score.entity.ScoreEventSummary;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

import jakarta.persistence.EntityManager;

/** DB 통합 테스트: 하루 리포트 저장, 직전 리포트 조회, 리포트에 담기지 않은 지표 조회 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VehicleScoreReportRepositoryTest {

	@Autowired
	private VehicleScoreReportRepository reportRepository;

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void 오늘과_반영_범위_카운트를_각각의_컬럼에_저장한다() {
		String vehicleId = "0DS614RB2TT925001";
		ScoreEventSummary today = ScoreEventSummary.builder().distanceM(38_000).overspeedKm(1.2).accelCount(3).brakeCount(4).turnCount(1).build();
		ScoreEventSummary window = ScoreEventSummary.builder().distanceM(1_000_000).overspeedKm(11).accelCount(28).brakeCount(22).turnCount(17).build();

		Long id = reportRepository.saveAndFlush(report(vehicleId, 89.72, today, window)).getId();
		entityManager.clear();

		VehicleScoreReport found = reportRepository.findById(id).orElseThrow();
		assertThat(found.getToday()).isEqualTo(today);
		assertThat(found.getWindow()).isEqualTo(window);
		assertThat(found.getReportDate()).isEqualTo(LocalDate.parse("2026-11-25"));
		assertThat(found.getRecentScores()).isEqualTo("[92.08,89.72]");
	}

	@Test
	void 점수가_미산출이면_null로_저장한다() {
		String vehicleId = "0DS614RB2TT925001";
		ScoreEventSummary empty = ScoreEventSummary.builder().build();

		Long id = reportRepository.saveAndFlush(report(vehicleId, null, empty, empty)).getId();
		entityManager.clear();

		assertThat(reportRepository.findById(id).orElseThrow().getScore()).isNull();
	}

	@Test
	void 직전_리포트는_가장_최근에_만든_리포트다() {
		String vehicleId = "0DS614RB2TT925001";
		ScoreEventSummary empty = ScoreEventSummary.builder().build();
		reportRepository.save(report(vehicleId, 90.0, empty, empty));
		VehicleScoreReport latest = reportRepository.saveAndFlush(report(vehicleId, 91.0, empty, empty));

		assertThat(reportRepository.findTopByVehicleIdOrderByIdDesc(vehicleId)).contains(latest);
	}

	@Test
	void 리포트에_담기지_않은_지표만_읽는다() {
		String vehicleId = "0DS614RB2TT925001";
		TripScoreMetric reported = metricRepository.save(metric(vehicleId, 1));
		TripScoreMetric fresh = metricRepository.save(metric(vehicleId, 2));
		ScoreEventSummary empty = ScoreEventSummary.builder().build();
		reported.assignReport(reportRepository.save(report(vehicleId, null, empty, empty)));
		metricRepository.flush();

		assertThat(metricRepository.findByVehicleIdAndReportIsNullOrderByEndedAtAscIdAsc(vehicleId)).containsExactly(fresh);
	}

	private TripScoreMetric metric(String vehicleId, long tid) {
		Trip trip = tripRepository.save(Trip.builder().vehicleId(vehicleId).tid(tid).build());
		return TripScoreMetric.builder()
				.trip(trip)
				.vehicleId(vehicleId)
				.endedAt(Instant.parse("2026-11-25T00:20:00Z"))
				.tally(ScoreTally.builder().distanceM(12_000).build())
				.build();
	}

	private static VehicleScoreReport report(String vehicleId, Double score, ScoreEventSummary today, ScoreEventSummary window) {
		return VehicleScoreReport.builder()
				.vehicleId(vehicleId)
				.reportDate(LocalDate.parse("2026-11-25"))
				.score(score)
				.penalty(score == null ? null : 100 - score)
				.recentScores("[92.08,89.72]")
				.windowSegmentCount(2)
				.today(today)
				.window(window)
				.build();
	}

}
