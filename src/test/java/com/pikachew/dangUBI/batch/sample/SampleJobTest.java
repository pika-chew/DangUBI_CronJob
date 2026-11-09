package com.pikachew.dangUBI.batch.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pikachew.dangUBI.batch.sample.config.SampleJobConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Job 통합 테스트: H2 메타 테이블 위에서 sampleJob 전체를 실행한다. */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class SampleJobTest {

	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@AfterEach
	void cleanUp() {
		jobRepositoryTestUtils.removeJobExecutions();
	}

	@Test
	void targetDate를_주면_COMPLETED로_끝난다() throws Exception {
		JobExecution execution = jobOperatorTestUtils.startJob(targetDate("2026-11-08"));

		assertThat(execution.getJobInstance().getJobName()).isEqualTo(SampleJobConfig.JOB_NAME);
		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(execution.getStepExecutions())
				.extracting(step -> step.getStepName())
				.containsExactly(SampleJobConfig.STEP_NAME);
	}

	@Test
	void 같은_targetDate로_이미_완료됐으면_다시_실행되지_않는다() throws Exception {
		jobOperatorTestUtils.startJob(targetDate("2026-11-08"));

		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(targetDate("2026-11-08")))
				.isInstanceOf(JobInstanceAlreadyCompleteException.class);
	}

	@Test
	void Step만_따로_실행할_수_있다() {
		JobExecution execution = jobOperatorTestUtils.startStep(SampleJobConfig.STEP_NAME, targetDate("2026-11-08"),
				new org.springframework.batch.infrastructure.item.ExecutionContext());

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
	}

	private static JobParameters targetDate(String value) {
		return new JobParametersBuilder().addString("targetDate", value).toJobParameters();
	}

}
