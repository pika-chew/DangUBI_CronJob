package com.pikachew.dangUBI.batch.reconcile.tasklet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;

/** 단위 테스트: 스프링 컨텍스트·DB 없이 가짜 저장소로 Tasklet만 검증한다. */
class ReconcileTaskletTest {

	private static final String TARGET_DATE = "2026-11-08";

	private final List<List<ObjectRef>> published = new ArrayList<>();

	@Test
	void Swift에_없는_객체만_재큐잉한다() {
		ReconcileTasklet tasklet = tasklet(
				objects("a", "b", "c"),
				objects("a", "c"),
				recordingPublisher());
		StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
		StepContribution contribution = stepExecution.createStepContribution();

		RepeatStatus status = tasklet.execute(contribution, null);
		stepExecution.apply(contribution);

		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
		assertThat(published).containsExactly(objects("b"));
		assertThat(stepExecution.getFilterCount()).isEqualTo(2);
		assertThat(stepExecution.getWriteCount()).isEqualTo(1);
	}

	@Test
	void 누락이_없으면_재큐잉을_호출하지_않는다() {
		ReconcileTasklet tasklet = tasklet(objects("a", "b"), objects("a", "b"), recordingPublisher());
		StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();

		tasklet.execute(contribution, null);

		assertThat(published).isEmpty();
	}

	@Test
	void Swift에만_있는_객체는_무시한다() {
		ReconcileTasklet tasklet = tasklet(objects("a"), objects("a", "extra"), recordingPublisher());
		StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();

		tasklet.execute(contribution, null);

		assertThat(published).isEmpty();
	}

	@Test
	void 재큐잉_건수가_누락_건수와_다르면_실패한다() {
		RequeuePublisher partialPublisher = missing -> missing.size() - 1;
		ReconcileTasklet tasklet = tasklet(objects("a", "b", "c"), objects("a"), partialPublisher);
		StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();

		assertThatThrownBy(() -> tasklet.execute(contribution, null))
				.isInstanceOf(GeneralException.class)
				.extracting(e -> ((GeneralException) e).getCode())
				.isEqualTo(ErrorStatus.RECONCILE_REQUEUE_INCOMPLETE);
	}

	@Test
	void 누락_목록은_키_순서로_정렬된다() {
		List<ObjectRef> missing = ReconcileTasklet.findMissing(objects("c", "a", "b"), objects());

		assertThat(missing).extracting(ObjectRef::key).containsExactly("a", "b", "c");
	}

	@Test
	void 저장소에는_targetDate를_그대로_넘긴다() {
		Map<String, LocalDate> requestedDates = new java.util.HashMap<>();
		ReconcileTasklet tasklet = new ReconcileTasklet(
				date -> {
					requestedDates.put("s3", date);
					return objects();
				},
				date -> {
					requestedDates.put("swift", date);
					return objects();
				},
				recordingPublisher(),
				TARGET_DATE);

		tasklet.execute(MetaDataInstanceFactory.createStepExecution().createStepContribution(), null);

		assertThat(requestedDates).containsEntry("s3", LocalDate.parse(TARGET_DATE))
				.containsEntry("swift", LocalDate.parse(TARGET_DATE));
	}

	private ReconcileTasklet tasklet(List<ObjectRef> sourceObjects, List<ObjectRef> rawObjects,
			RequeuePublisher publisher) {
		return new ReconcileTasklet(date -> sourceObjects, date -> rawObjects, publisher, TARGET_DATE);
	}

	private RequeuePublisher recordingPublisher() {
		return missing -> {
			published.add(List.copyOf(missing));
			return missing.size();
		};
	}

	private static List<ObjectRef> objects(String... keys) {
		return java.util.Arrays.stream(keys).map(key -> new ObjectRef(key, 1L)).toList();
	}

}
