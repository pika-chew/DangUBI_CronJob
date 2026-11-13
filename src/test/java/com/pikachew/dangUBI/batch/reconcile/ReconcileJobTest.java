package com.pikachew.dangUBI.batch.reconcile;

import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_TARGET_DATE_PARAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;

import com.pikachew.dangUBI.batch.common.port.ObjectRef;
import com.pikachew.dangUBI.batch.common.port.RawObjectStore;
import com.pikachew.dangUBI.batch.common.port.RequeuePublisher;
import com.pikachew.dangUBI.batch.common.port.SourceObjectStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Job 통합 테스트: H2 메타 테이블 위에서 reconcileJob 전체를 실행한다. 외부 저장소는 Mock으로 바꾼다. */
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class ReconcileJobTest {

	private static final LocalDate TARGET_DATE = LocalDate.parse("2026-11-08");

	@Autowired
	private JobOperatorTestUtils jobOperatorTestUtils;

	@Autowired
	private JobRepositoryTestUtils jobRepositoryTestUtils;

	@MockitoBean
	private SourceObjectStore sourceObjectStore;

	@MockitoBean
	private RawObjectStore rawObjectStore;

	@MockitoBean
	private RequeuePublisher requeuePublisher;

	@Autowired
	@Qualifier(RECONCILE_JOB_NAME)
	private Job reconcileJob;

	/** Job이 여러 개면 JobOperatorTestUtils가 Job을 고르지 않으므로 직접 지정한다 */
	@BeforeEach
	void setUpJob() {
		jobOperatorTestUtils.setJob(reconcileJob);
	}

	@AfterEach
	void cleanUp() {
		jobRepositoryTestUtils.removeJobExecutions();
	}

	@Test
	void 누락_객체를_재큐잉하고_COMPLETED로_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b", "c"));
		given(rawObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a"));
		given(requeuePublisher.publish(any())).willAnswer(invocation -> invocation.<List<?>>getArgument(0).size());

		JobExecution execution = jobOperatorTestUtils.startJob(targetDate("2026-11-08"));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		verify(requeuePublisher).publish(objects("b", "c"));
		StepExecution step = execution.getStepExecutions().iterator().next();
		assertThat(step.getStepName()).isEqualTo(RECONCILE_STEP_NAME);
		assertThat(step.getFilterCount()).isEqualTo(1);
		assertThat(step.getWriteCount()).isEqualTo(2);
	}

	@Test
	void 저장소_조회가_실패하면_FAILED로_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(TARGET_DATE)).willThrow(new IllegalStateException("S3 접속 실패"));

		JobExecution execution = jobOperatorTestUtils.startJob(targetDate("2026-11-08"));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(execution.getAllFailureExceptions()).anyMatch(e -> e.getMessage().contains("S3 접속 실패"));
	}

	@Test
	void targetDate가_없으면_실행되지_않는다() {
		assertThatThrownBy(() -> jobOperatorTestUtils.startJob(new JobParameters()))
				.isInstanceOf(InvalidJobParametersException.class);
	}

	private static JobParameters targetDate(String value) {
		return new JobParametersBuilder().addString(RECONCILE_TARGET_DATE_PARAM, value).toJobParameters();
	}

	private static List<ObjectRef> objects(String... keys) {
		return java.util.Arrays.stream(keys).map(key -> new ObjectRef(key, 1L)).toList();
	}

}
