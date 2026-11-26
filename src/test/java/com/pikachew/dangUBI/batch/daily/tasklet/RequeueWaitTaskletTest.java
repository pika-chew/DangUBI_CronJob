package com.pikachew.dangUBI.batch.daily.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import com.pikachew.dangUBI.batch.daily.config.DailyProperties;
import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;

/** 단위 테스트: S3에는 있는데 Swift에 없는 파일이 복사될 때까지 기다리고, 제한 시간이 지나면 남은 채로 끝낸다 */
class RequeueWaitTaskletTest {

	private static final LocalDate TARGET_DATE = LocalDate.parse("2026-11-25");
	private static final DailyProperties PROPERTIES = new DailyProperties(Duration.ofMillis(200), Duration.ofMillis(10), null);

	private final SourceObjectStore sourceObjectStore = mock(SourceObjectStore.class);
	private final RawObjectStore rawObjectStore = mock(RawObjectStore.class);
	private final RequeueWaitTasklet tasklet =
			new RequeueWaitTasklet(sourceObjectStore, rawObjectStore, PROPERTIES, TARGET_DATE.toString());

	@Test
	void Swift에_없는_파일이_없으면_기다리지_않고_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b"));
		given(rawObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b", "extra"));
		StepContribution contribution = contribution();

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		verify(rawObjectStore, times(1)).listObjects(TARGET_DATE);
		assertThat(contribution.getWriteCount()).isZero();
	}

	@Test
	void Swift에_없던_파일이_모두_복사되면_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b", "c"));
		given(rawObjectStore.listObjects(TARGET_DATE))
				.willReturn(objects("a"))
				.willReturn(objects("a", "b"))
				.willReturn(objects("a", "b", "c"));
		StepContribution contribution = contribution();

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getWriteCount()).isEqualTo(2);
		assertThat(contribution.getFilterCount()).isZero();
		verify(sourceObjectStore, times(1)).listObjects(TARGET_DATE);
	}

	@Test
	void 제한_시간이_지나면_복사되지_않은_파일을_남긴_채_끝난다() throws Exception {
		given(sourceObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a", "b", "c"));
		given(rawObjectStore.listObjects(TARGET_DATE)).willReturn(objects("a")).willReturn(objects("a", "b"));
		StepContribution contribution = contribution();

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getWriteCount()).isEqualTo(1);
		assertThat(contribution.getFilterCount()).isEqualTo(1);
	}

	@Test
	void 누락이_수천_건이어도_Job_실행_기록에_남기지_않고_기다린다() throws Exception {
		List<ObjectRef> source = longKeys(3000);
		given(sourceObjectStore.listObjects(TARGET_DATE)).willReturn(source);
		given(rawObjectStore.listObjects(TARGET_DATE)).willReturn(List.of()).willReturn(source);
		StepContribution contribution = contribution();

		assertThat(tasklet.execute(contribution, null)).isEqualTo(RepeatStatus.FINISHED);
		assertThat(contribution.getWriteCount()).isEqualTo(3000);
		assertThat(contribution.getStepExecution().getJobExecution().getExecutionContext().isEmpty()).isTrue();
	}

	private static StepContribution contribution() {
		return MetaDataInstanceFactory.createStepExecution().createStepContribution();
	}

	private static List<ObjectRef> objects(String... keys) {
		return Arrays.stream(keys).map(key -> new ObjectRef(key, 1L)).toList();
	}

	/** 운영 키와 비슷한 길이(약 120자)의 키 */
	private static List<ObjectRef> longKeys(int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> new ObjectRef("firehose/processed/year=2026/month=11/day=25/hour=%02d/dangubi-firehose-1-2026-11-25-%05d-0a1b2c3d-4e5f-6789-abcd-ef0123456789.jsonl.gz"
						.formatted(i % 24, i), 1L))
				.toList();
	}

}
