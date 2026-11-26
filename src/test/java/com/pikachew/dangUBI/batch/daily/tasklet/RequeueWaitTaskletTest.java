package com.pikachew.dangUBI.batch.daily.tasklet;

import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_MISSING_KEYS_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import com.pikachew.dangUBI.batch.daily.config.DailyProperties;
import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;

/** 단위 테스트: 재큐잉한 키가 Swift에 복사될 때까지 기다리고, 제한 시간이 지나면 남은 채로 끝낸다 */
class RequeueWaitTaskletTest {

	private static final LocalDate TARGET_DATE = LocalDate.parse("2026-11-25");
	private static final DailyProperties PROPERTIES = new DailyProperties(Duration.ofMillis(200), Duration.ofMillis(10), null);

	private final RawObjectStore rawObjectStore = mock(RawObjectStore.class);
	private final RequeueWaitTasklet tasklet = new RequeueWaitTasklet(rawObjectStore, PROPERTIES, TARGET_DATE.toString());

	@Test
	void 재큐잉한_키가_없으면_Swift를_보지_않고_끝난다() throws Exception {
		StepContribution contribution = contribution();

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		verify(rawObjectStore, never()).listObjects(TARGET_DATE);
	}

	@Test
	void 재큐잉한_키가_모두_복사되면_끝난다() throws Exception {
		StepContribution contribution = contribution("b", "c");
		given(rawObjectStore.listObjects(TARGET_DATE))
				.willReturn(objects("a"))
				.willReturn(objects("a", "b"))
				.willReturn(objects("a", "b", "c"));

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getWriteCount()).isEqualTo(2);
		assertThat(contribution.getFilterCount()).isZero();
	}

	@Test
	void 제한_시간이_지나면_복사되지_않은_키를_남긴_채_끝난다() throws Exception {
		StepContribution contribution = contribution("b", "c");
		given(rawObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b"));

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getWriteCount()).isEqualTo(1);
		assertThat(contribution.getFilterCount()).isEqualTo(1);
	}

	private static StepContribution contribution(String... missingKeys) {
		StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
		if (missingKeys.length > 0) {
			stepExecution.getJobExecution().getExecutionContext()
					.put(RECONCILE_MISSING_KEYS_CONTEXT, new ArrayList<>(Arrays.asList(missingKeys)));
		}
		return stepExecution.createStepContribution();
	}

	private static List<ObjectRef> objects(String... keys) {
		return Arrays.stream(keys).map(key -> new ObjectRef(key, 1L)).toList();
	}

}
