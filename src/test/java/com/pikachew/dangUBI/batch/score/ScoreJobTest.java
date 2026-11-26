package com.pikachew.dangUBI.batch.score;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_METRIC_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_REPORT_STEP_NAME;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.score.entity.VehicleScoreReport;
import com.pikachew.dangUBI.domain.score.repository.VehicleScoreReportRepository;
import com.pikachew.dangUBI.domain.score.repository.ScoreSegmentRepository;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

/**
 * Job 통합 테스트: H2 위에서 scoreJob 전체를 실행한다. 적재는 ingestJob 대신 Repository로 직접 넣는다.
 * 테스트 프로필은 test 기준(구간 50km, 반영 범위 600km)이다.
 * 실제 실행처럼 파라미터 없이 띄우면 RunIdIncrementer가 매번 새 실행을 만든다.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class ScoreJobTest {

	private static final String VIN = "0DS614RB2TT925001";

	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripMicrotripRepository microtripRepository;

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private ScoreSegmentRepository segmentRepository;

	@Autowired
	private VehicleScoreReportRepository reportRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	@Qualifier(SCORE_JOB_NAME)
	private Job scoreJob;

	private long tid;

	/** Job이 여러 개면 JobOperatorTestUtils가 Job을 고르지 않으므로 직접 지정한다 */
	@BeforeEach
	void setUpJob() {
		jobOperatorTestUtils.setJob(scoreJob);
	}

	@AfterEach
	void cleanUp() {
		jobRepositoryTestUtils.removeJobExecutions();
		outboxRepository.deleteAllInBatch();
		metricRepository.deleteAllInBatch();
		reportRepository.deleteAllInBatch();
		segmentRepository.deleteAllInBatch();
		microtripRepository.deleteAllInBatch();
		tripRepository.deleteAllInBatch();
	}

	@Test
	void 지표와_구간과_리포트를_만들고_COMPLETED로_끝난다() throws Exception {
		completedTrip(30_000, "2026-11-25T01:00:00Z", 0x10);
		completedTrip(30_000, "2026-11-25T02:00:00Z", 0x20);
		inProgressTrip();

		JobExecution execution = jobOperatorTestUtils.startJob();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
				.containsExactly(SCORE_METRIC_STEP_NAME, SCORE_REPORT_STEP_NAME);
		assertThat(metricRepository.count()).isEqualTo(2);
		assertThat(segmentRepository.count()).isEqualTo(1);

		VehicleScoreReport report = reportRepository.findTopByVehicleIdOrderByIdDesc(VIN).orElseThrow();
		// (급가속 1회 × 90 + 급감속 1회 × 200) / 60km
		assertThat(report.getPenalty()).isCloseTo(290 / 60.0, within(1e-9));
		assertThat(report.getToday().getDistanceM()).isEqualTo(60_000);
		assertThat(outboxRepository.findAllByAggregateTypeAndAggregateIdOrderById(
				OutboxAggregateType.VEHICLE_SCORE_REPORT, report.getId()))
				.extracting(Outbox::getEventType).containsExactly(OutboxEventType.VEHICLE_SCORE_REPORTED);
	}

	@Test
	void 다시_실행하면_새_실행으로_새로_종료된_trip만_처리한다() throws Exception {
		completedTrip(30_000, "2026-11-25T01:00:00Z", 0x10);
		JobExecution first = jobOperatorTestUtils.startJob();

		JobExecution nothingNew = jobOperatorTestUtils.startJob();

		assertThat(nothingNew.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(nothingNew.getJobInstanceId()).isNotEqualTo(first.getJobInstanceId());
		assertThat(metricRepository.count()).isEqualTo(1);
		assertThat(reportRepository.count()).isEqualTo(1);
		assertThat(outboxRepository.count()).isEqualTo(1);

		completedTrip(30_000, "2026-11-26T01:00:00Z", 0x00);
		jobOperatorTestUtils.startJob();

		assertThat(metricRepository.count()).isEqualTo(2);
		assertThat(segmentRepository.count()).isEqualTo(1);
		assertThat(reportRepository.count()).isEqualTo(2);
		assertThat(outboxRepository.count()).isEqualTo(2);
	}

	@Test
	void 구간을_닫지_못하면_점수는_미산출로_리포트를_남긴다() throws Exception {
		completedTrip(20_000, "2026-11-25T01:00:00Z", 0x10);

		JobExecution execution = jobOperatorTestUtils.startJob();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(segmentRepository.count()).isZero();
		VehicleScoreReport report = reportRepository.findTopByVehicleIdOrderByIdDesc(VIN).orElseThrow();
		assertThat(report.getScore()).isNull();
		assertThat(report.getToday().getBrakeCount()).isEqualTo(1);
	}

	/** dis가 distanceM이고, 종료 1분 전에 eventMask가 켜진 microtrip 하나가 있는 종료된 trip */
	private void completedTrip(int distanceM, String endedAt, int eventMask) {
		Instant end = Instant.parse(endedAt);
		Trip trip = Trip.builder().vehicleId(VIN).tid(++tid).build();
		trip.complete(trip(VIN, tid, end.minusSeconds(1200).toString(), endedAt, distanceM));
		trip.recordIngestedAt(Instant.now());
		tripRepository.save(trip);
		microtripRepository.save(TripMicrotrip.builder()
				.trip(trip)
				.idempotencyKey("mt-" + tid)
				.collectedAt(end.minusSeconds(60))
				.speed(60)
				.eventMask((short) eventMask)
				.build());
	}

	private void inProgressTrip() {
		Trip trip = tripRepository.save(Trip.builder().vehicleId(VIN).tid(++tid).build());
		microtripRepository.save(TripMicrotrip.builder()
				.trip(trip)
				.idempotencyKey("mt-" + tid)
				.collectedAt(Instant.parse("2026-11-25T03:00:00Z"))
				.speed(60)
				.eventMask((short) 0x10)
				.build());
	}

}
