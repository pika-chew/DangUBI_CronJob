package com.pikachew.dangUBI.batch.ingest;

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
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.common.consts.IngestStaticValues;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.trip.entity.TripStatus;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;
import com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException;

/**
 * Job 통합 테스트: H2 위에서 ingestJob 전체를 실행한다. Swift(포트)는 Mock으로 바꾼다.
 * 테스트 프로필은 chunk 크기가 1이라 파일 하나마다 커밋된다.
 */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class IngestJobTest {

	private static final String DATE = "2026-11-10";
	private static final String FILE_A = "firehose/processed/2026/11/10/03/a.jsonl.gz";
	private static final String FILE_B = "firehose/processed/2026/11/10/04/b.jsonl.gz";

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

	@MockitoBean
	private TelemetryFileReader telemetryFileReader;

	@AfterEach
	void cleanUp() {
		jobRepositoryTestUtils.removeJobExecutions();
		microtripRepository.deleteAllInBatch();
		tripRepository.deleteAllInBatch();
		driverRepository.deleteAllInBatch();
	}

	@Test
	void 하루치_파일을_적재하고_COMPLETED로_끝난다() throws Exception {
		givenTwoFiles();

		JobExecution execution = jobOperatorTestUtils.startJob(params(DATE));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		StepExecution step = execution.getStepExecutions().iterator().next();
		assertThat(step.getStepName()).isEqualTo(IngestStaticValues.STEP_NAME);
		assertThat(step.getReadCount()).isEqualTo(2);
		assertThat(step.getWriteCount()).isEqualTo(2);
		assertThat(microtripRepository.count()).isEqualTo(1);
		assertThat(tripRepository.findByVehicleIdAndTid("vehicle-001", 354).orElseThrow().getStatus())
				.isEqualTo(TripStatus.COMPLETED);
	}

	@Test
	void rerun으로_다시_적재해도_결과가_같다() throws Exception {
		givenTwoFiles();
		jobOperatorTestUtils.startJob(params(DATE));

		JobExecution rerun = jobOperatorTestUtils.startJob(new JobParametersBuilder(params(DATE))
				.addLong(IngestStaticValues.RERUN, 2L)
				.toJobParameters());

		assertThat(rerun.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(driverRepository.count()).isEqualTo(1);
		assertThat(tripRepository.count()).isEqualTo(1);
		assertThat(microtripRepository.count()).isEqualTo(1);
	}

	@Test
	void 이미_완료된_날짜는_같은_파라미터로_다시_실행되지_않는다() throws Exception {
		givenTwoFiles();
		jobOperatorTestUtils.startJob(params(DATE));

		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(params(DATE)))
				.isInstanceOf(JobInstanceAlreadyCompleteException.class);
	}

	@Test
	void 파일을_읽을_수_없으면_FAILED로_끝난다() throws Exception {
		given(telemetryFileReader.listFiles(LocalDate.parse(DATE))).willReturn(List.of(FILE_A));
		given(telemetryFileReader.read(FILE_A)).willThrow(brokenFile(FILE_A));

		JobExecution execution = jobOperatorTestUtils.startJob(params(DATE));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(execution.getAllFailureExceptions()).anyMatch(IngestJobTest::causedByUnreadableFile);
	}

	/** Spring Batch 6은 Step 예외를 FatalStepExecutionException으로 감싸므로 원인을 따라가며 찾는다 */
	private static boolean causedByUnreadableFile(Throwable thrown) {
		for (Throwable e = thrown; e != null; e = e.getCause()) {
			if (e instanceof GeneralException general && general.getCode() == ErrorStatus.INGEST_FILE_UNREADABLE) {
				return true;
			}
		}
		return false;
	}

	@Test
	void 실패한_실행을_다시_돌리면_멈춘_파일부터_이어서_처리한다() throws Exception {
		given(telemetryFileReader.listFiles(LocalDate.parse(DATE))).willReturn(List.of(FILE_A, FILE_B));
		given(telemetryFileReader.read(FILE_A)).willReturn(fileA());
		given(telemetryFileReader.read(FILE_B)).willThrow(brokenFile(FILE_B)).willReturn(fileB());

		JobExecution failed = jobOperatorTestUtils.startJob(params(DATE));
		JobExecution restarted = jobOperatorTestUtils.startJob(params(DATE));

		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
		verify(telemetryFileReader, times(1)).read(FILE_A);
		verify(telemetryFileReader, times(2)).read(FILE_B);
		assertThat(tripRepository.findByVehicleIdAndTid("vehicle-001", 354).orElseThrow().getStatus())
				.isEqualTo(TripStatus.COMPLETED);
	}

	@Test
	void targetDate가_없으면_실행되지_않는다() {
		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(new JobParameters()))
				.isInstanceOf(InvalidJobParametersException.class);
	}

	private void givenTwoFiles() {
		given(telemetryFileReader.listFiles(LocalDate.parse(DATE))).willReturn(List.of(FILE_A, FILE_B));
		given(telemetryFileReader.read(FILE_A)).willReturn(fileA());
		given(telemetryFileReader.read(FILE_B)).willReturn(fileB());
	}

	private static ParsedTelemetryFile fileA() {
		return new ParsedTelemetryFile(FILE_A, List.of(),
				List.of(microtrip("vehicle-001", 354, "m1", "2026-11-10T10:30:00Z")), 0);
	}

	private static ParsedTelemetryFile fileB() {
		return new ParsedTelemetryFile(FILE_B,
				List.of(trip("vehicle-001", 354, "2026-11-10T10:00:00Z", "2026-11-10T11:00:00Z", 30000)), List.of(), 0);
	}

	private static TelemetryFileFormatException brokenFile(String key) {
		return new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + key, new IOException("bad gzip"));
	}

	private static JobParameters params(String targetDate) {
		return new JobParametersBuilder().addString(IngestStaticValues.TARGET_DATE, targetDate).toJobParameters();
	}

}
