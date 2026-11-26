package com.pikachew.dangUBI.batch.common.runner;

import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_JOB_NAME;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.microtrip;
import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.batch.autoconfigure.BatchProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.common.consts.IngestStaticValues;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;
import com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException;

/**
 * DB 통합 테스트: 프로세스가 죽어 STARTED로 남은 ingestJob 실행을 정리하고 이어서 재시작하는지 본다.
 * 테스트 프로필은 chunk 크기가 1이라 파일 하나마다 커밋된다.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class StaleJobExecutionRecoveryRunnerTest {

	private static final String DATE = "2026-11-10";
	private static final String FILE_A = "firehose/processed/year=2026/month=11/day=10/hour=03/a.jsonl.gz";
	private static final String FILE_B = "firehose/processed/year=2026/month=11/day=10/hour=04/b.jsonl.gz";

	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobOperator jobOperator;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripMicrotripRepository microtripRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@MockitoBean
	private TelemetryFileReader telemetryFileReader;

	@Autowired
	@Qualifier(INGEST_JOB_NAME)
	private Job ingestJob;

	@BeforeEach
	void setUpJob() {
		jobOperatorTestUtils.setJob(ingestJob);
	}

	@AfterEach
	void cleanUp() {
		jobRepositoryTestUtils.removeJobExecutions();
		outboxRepository.deleteAllInBatch();
		microtripRepository.deleteAllInBatch();
		tripRepository.deleteAllInBatch();
		driverRepository.deleteAllInBatch();
	}

	@Test
	void 갑자기_죽어_STARTED로_남은_실행을_정리하면_멈춘_파일부터_이어서_처리한다() throws Exception {
		long crashedId = crashedAfterFirstFile();
		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(params()))
				.isInstanceOf(JobExecutionAlreadyRunningException.class);

		runner(INGEST_JOB_NAME).run(null);
		JobExecution restarted = jobOperatorTestUtils.startJob(params());

		JobExecution recovered = jobRepository.getJobExecution(crashedId);
		assertThat(recovered.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(recovered.getEndTime()).isNotNull();
		assertThat(recovered.getStepExecutions()).extracting(StepExecution::getStatus).containsOnly(BatchStatus.FAILED);
		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstanceId()).isEqualTo(recovered.getJobInstanceId());
		verify(telemetryFileReader, times(1)).read(FILE_A);
	}

	@Test
	void 다른_Job의_실행은_건드리지_않는다() throws Exception {
		long crashedId = crashedAfterFirstFile();

		runner("scoreJob").run(null);

		assertThat(jobRepository.getJobExecution(crashedId).getStatus()).isEqualTo(BatchStatus.STARTED);
	}

	@Test
	void 실행할_Job_이름이_없으면_아무것도_하지_않는다() throws Exception {
		long crashedId = crashedAfterFirstFile();

		runner(null).run(null);

		assertThat(jobRepository.getJobExecution(crashedId).getStatus()).isEqualTo(BatchStatus.STARTED);
	}

	/**
	 * FILE_A까지 커밋한 뒤 프로세스가 죽은 상태: Job과 Step이 STARTED이고 종료 시각이 없다. 실행 id를 돌려준다.
	 * JobExecution을 돌려주면 @SpringBatchTest가 스코프용 팩토리 메서드로 보고 먼저 호출한다.
	 */
	private long crashedAfterFirstFile() throws Exception {
		given(telemetryFileReader.listFiles(LocalDate.parse(DATE))).willReturn(List.of(FILE_A, FILE_B));
		given(telemetryFileReader.read(FILE_A)).willReturn(fileA());
		given(telemetryFileReader.read(FILE_B)).willThrow(brokenFile()).willReturn(fileB());
		JobExecution execution = jobOperatorTestUtils.startJob(params());
		for (StepExecution step : execution.getStepExecutions()) {
			step.setStatus(BatchStatus.STARTED);
			step.setEndTime(null);
			jobRepository.update(step);
		}
		execution.setStatus(BatchStatus.STARTED);
		execution.setEndTime(null);
		jobRepository.update(execution);
		return execution.getId();
	}

	private StaleJobExecutionRecoveryRunner runner(String jobName) {
		BatchProperties properties = new BatchProperties();
		properties.getJob().setName(jobName);
		return new StaleJobExecutionRecoveryRunner(properties, jobRepository, jobOperator);
	}

	private static ParsedTelemetryFile fileA() {
		return new ParsedTelemetryFile(FILE_A, List.of(),
				List.of(microtrip("vehicle-001", 354, "m1", "2026-11-10T10:30:00Z")), 0);
	}

	private static ParsedTelemetryFile fileB() {
		return new ParsedTelemetryFile(FILE_B,
				List.of(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000)), List.of(), 0);
	}

	private static TelemetryFileFormatException brokenFile() {
		return new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + FILE_B, new IOException("bad gzip"));
	}

	private static JobParameters params() {
		return new JobParametersBuilder().addString(IngestStaticValues.INGEST_TARGET_DATE_PARAM, DATE).toJobParameters();
	}

}
