package com.pikachew.dangUBI.batch.daily;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_REQUEUE_WAIT_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_PARAM;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_FILE_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_METRIC_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_REPORT_STEP_NAME;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.microtrip;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;
import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.score.repository.ScoreSegmentRepository;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;
import com.pikachew.dangUBI.domain.score.repository.VehicleScoreReportRepository;
import com.pikachew.dangUBI.domain.trip.entity.TripStatus;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;
import com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException;

/**
 * Job 통합 테스트: H2 위에서 dailyJob 전체를 실행한다. S3·Swift·SQS(포트)는 Mock으로 바꾼다.
 * targetDate는 --ubi.batch.daily.target-date로 고정한다. 테스트 프로필은 복사 대기 300ms, 점수 대기 0초다.
 */
@SpringBatchTest
@SpringBootTest(properties = "ubi.batch.daily.target-date=2026-11-10")
@ActiveProfiles("test")
class DailyJobTest {

	private static final LocalDate DATE = LocalDate.parse("2026-11-10");
	private static final String FILE_A = "firehose/processed/year=2026/month=11/day=10/hour=03/a.jsonl.gz";
	private static final String FILE_B = "firehose/processed/year=2026/month=11/day=10/hour=04/b.jsonl.gz";

	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	private DriverRepository driverRepository;

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

	@MockitoBean
	private SourceObjectStore sourceObjectStore;

	@MockitoBean
	private RawObjectStore rawObjectStore;

	@MockitoBean
	private RequeuePublisher requeuePublisher;

	@MockitoBean
	private TelemetryFileReader telemetryFileReader;

	@Autowired
	@Qualifier(DAILY_JOB_NAME)
	private Job dailyJob;

	/** Job이 여러 개면 JobOperatorTestUtils가 Job을 고르지 않으므로 직접 지정한다 */
	@BeforeEach
	void setUpJob() {
		jobOperatorTestUtils.setJob(dailyJob);
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
		driverRepository.deleteAllInBatch();
	}

	@Test
	void 누락_파일을_재큐잉하고_복사를_기다린_뒤_적재와_점수까지_끝낸다() throws Exception {
		given(sourceObjectStore.listObjects(DATE)).willReturn(objects(FILE_A, FILE_B));
		// reconcile과 대기 시작 때는 B가 없고, 기다리는 동안 수집 워커가 B를 복사한다
		given(rawObjectStore.listObjects(DATE))
				.willReturn(objects(FILE_A))
				.willReturn(objects(FILE_A))
				.willReturn(objects(FILE_A, FILE_B));
		given(requeuePublisher.publish(any())).willAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
		givenTwoFiles();

		JobExecution execution = jobOperatorTestUtils.startJob();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(execution.getJobParameters().getString(DAILY_TARGET_DATE_PARAM)).isEqualTo("2026-11-10");
		assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName).containsExactly(
				RECONCILE_STEP_NAME, DAILY_REQUEUE_WAIT_STEP_NAME, INGEST_FILE_STEP_NAME, SCORE_METRIC_STEP_NAME, SCORE_REPORT_STEP_NAME);
		verify(requeuePublisher).publish(objects(FILE_B));
		assertThat(steps(execution).get(DAILY_REQUEUE_WAIT_STEP_NAME).getWriteCount()).isEqualTo(1);
		assertThat(tripRepository.findByVehicleIdAndTid("vehicle-001", 354).orElseThrow().getStatus()).isEqualTo(TripStatus.COMPLETED);
		assertThat(metricRepository.count()).isEqualTo(1);
		assertThat(reportRepository.count()).isEqualTo(1);
		assertThat(outboxRepository.findAll()).extracting(Outbox::getEventType)
				.contains(OutboxEventType.VEHICLE_SCORE_REPORTED);
	}

	@Test
	void reconcile이_실패해도_적재와_점수는_끝내고_Job은_FAILED로_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(DATE)).willReturn(objects(FILE_A, FILE_B));
		given(rawObjectStore.listObjects(DATE)).willReturn(objects(FILE_A));
		// 재큐잉이 하나도 들어가지 않아 reconcileStep이 실패하고, 복사도 되지 않아 대기는 제한 시간으로 끝난다
		given(requeuePublisher.publish(any())).willReturn(0);
		given(telemetryFileReader.listFiles(DATE)).willReturn(List.of(FILE_A));
		given(telemetryFileReader.read(FILE_A)).willReturn(fileWithCompletedTrip(FILE_A));

		JobExecution execution = jobOperatorTestUtils.startJob();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
		// 흐름이 지나쳐 간 실패 Step은 ABANDONED가 되어 같은 날짜로 다시 띄워도 다시 돌지 않는다. 종료 코드는 FAILED로 남는다
		assertThat(steps(execution).get(RECONCILE_STEP_NAME).getStatus()).isEqualTo(BatchStatus.ABANDONED);
		assertThat(steps(execution).get(RECONCILE_STEP_NAME).getExitStatus().getExitCode()).isEqualTo(ExitStatus.FAILED.getExitCode());
		assertThat(steps(execution).get(DAILY_REQUEUE_WAIT_STEP_NAME).getFilterCount()).isEqualTo(1);
		assertThat(steps(execution).get(INGEST_FILE_STEP_NAME).getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(steps(execution).get(SCORE_REPORT_STEP_NAME).getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(reportRepository.count()).isEqualTo(1);
	}

	@Test
	void 적재가_실패하면_점수를_돌리지_않고_FAILED로_멈춘다() throws Exception {
		given(sourceObjectStore.listObjects(DATE)).willReturn(objects(FILE_A));
		given(rawObjectStore.listObjects(DATE)).willReturn(objects(FILE_A));
		given(telemetryFileReader.listFiles(DATE)).willReturn(List.of(FILE_A));
		given(telemetryFileReader.read(FILE_A)).willThrow(
				new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + FILE_A, new IOException("bad gzip")));

		JobExecution execution = jobOperatorTestUtils.startJob();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(execution.getStepExecutions()).extracting(StepExecution::getStepName)
				.containsExactly(RECONCILE_STEP_NAME, DAILY_REQUEUE_WAIT_STEP_NAME, INGEST_FILE_STEP_NAME);
		assertThat(metricRepository.count()).isZero();
	}

	@Test
	void 실패한_날을_다시_띄우면_같은_날짜로_실패한_Step부터_이어서_처리한다() throws Exception {
		given(sourceObjectStore.listObjects(DATE)).willReturn(objects(FILE_A));
		given(rawObjectStore.listObjects(DATE)).willReturn(objects(FILE_A));
		given(telemetryFileReader.listFiles(DATE)).willReturn(List.of(FILE_A));
		given(telemetryFileReader.read(FILE_A))
				.willThrow(new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + FILE_A, new IOException("bad gzip")))
				.willReturn(fileWithCompletedTrip(FILE_A));

		JobExecution failed = jobOperatorTestUtils.startJob();
		JobExecution restarted = jobOperatorTestUtils.startJob();

		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
		assertThat(restarted.getStepExecutions()).extracting(StepExecution::getStepName)
				.containsExactly(INGEST_FILE_STEP_NAME, SCORE_METRIC_STEP_NAME, SCORE_REPORT_STEP_NAME);
		assertThat(reportRepository.count()).isEqualTo(1);
	}

	private void givenTwoFiles() {
		given(telemetryFileReader.listFiles(DATE)).willReturn(List.of(FILE_A, FILE_B));
		given(telemetryFileReader.read(FILE_A)).willReturn(new ParsedTelemetryFile(FILE_A, List.of(),
				List.of(microtrip("vehicle-001", 354, "m1", "2026-11-10T10:30:00Z")), 0));
		given(telemetryFileReader.read(FILE_B)).willReturn(new ParsedTelemetryFile(FILE_B,
				List.of(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000)), List.of(), 0));
	}

	private static ParsedTelemetryFile fileWithCompletedTrip(String key) {
		return new ParsedTelemetryFile(key,
				List.of(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000)),
				List.of(microtrip("vehicle-001", 354, "m1", "2026-11-10T10:30:00Z")), 0);
	}

	/** StepExecution을 돌려주는 메서드는 @SpringBatchTest가 Step 실행 팩토리로 착각하므로 Map으로 돌려준다 */
	private static Map<String, StepExecution> steps(JobExecution execution) {
		return execution.getStepExecutions().stream()
				.collect(Collectors.toMap(StepExecution::getStepName, Function.identity()));
	}

	private static List<ObjectRef> objects(String... keys) {
		return Arrays.stream(keys).map(key -> new ObjectRef(key, 1L)).toList();
	}

}
