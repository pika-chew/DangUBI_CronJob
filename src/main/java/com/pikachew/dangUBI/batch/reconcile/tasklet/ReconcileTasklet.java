package com.pikachew.dangUBI.batch.reconcile.tasklet;

import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_TARGET_DATE_EXPRESSION;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.pikachew.dangUBI.batch.common.port.ObjectRef;
import com.pikachew.dangUBI.batch.common.port.RawObjectStore;
import com.pikachew.dangUBI.batch.common.port.RequeuePublisher;
import com.pikachew.dangUBI.batch.common.port.SourceObjectStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * S3 목록과 Swift 목록을 비교해 Swift에 없는 객체를 재큐잉한다. 목록 비교는 한 번에 끝나는 작업이라 Tasklet으로 둔다.
 *
 * 비교 기준은 객체 키다. S3와 Swift가 같은 키를 쓴다고 가정한다.
 * StepExecution 기록: filterCount = 이미 Swift에 있는 객체 수, writeCount = 재큐잉 건수. 둘을 더하면 S3 객체 수다.
 *
 * TODO: 수집 워커가 Swift에 저장할 때 키를 바꾸면(접두어 추가 등) 키 변환 규칙을 수집 워커와 맞춘다.
 * TODO: 크기까지 비교할지 정한다. 키는 있지만 크기가 다르면(업로드 중 끊김) 누락으로 볼지.
 * TODO: 하루 객체 수가 많아 두 목록을 메모리에 올리기 어려우면, 키 순서로 정렬된 두 목록을 병합하며 비교하는 방식으로 바꾼다.
 */
@Slf4j
@Component
@StepScope
public class ReconcileTasklet implements Tasklet {

	private final SourceObjectStore sourceObjectStore;
	private final RawObjectStore rawObjectStore;
	private final RequeuePublisher requeuePublisher;
	private final LocalDate targetDate;

	public ReconcileTasklet(SourceObjectStore sourceObjectStore, RawObjectStore rawObjectStore,
			RequeuePublisher requeuePublisher, @Value(RECONCILE_TARGET_DATE_EXPRESSION) String targetDate) {
		this.sourceObjectStore = sourceObjectStore;
		this.rawObjectStore = rawObjectStore;
		this.requeuePublisher = requeuePublisher;
		this.targetDate = LocalDate.parse(targetDate);
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		List<ObjectRef> sourceObjects = sourceObjectStore.listObjects(targetDate);
		List<ObjectRef> rawObjects = rawObjectStore.listObjects(targetDate);

		List<ObjectRef> missing = findMissing(sourceObjects, rawObjects);
		log.info("reconcile {}: S3 {}건, Swift {}건, 누락 {}건",
				targetDate, sourceObjects.size(), rawObjects.size(), missing.size());

		int requeued = missing.isEmpty() ? 0 : requeuePublisher.publish(missing);
		if (requeued != missing.size()) {
			// 일부만 들어갔으면 Step을 실패시켜 다시 돌게 한다. 이미 들어간 객체는 수집 워커가 한 번 더 옮길 뿐이라 안전하다.
			throw new IllegalStateException(
					"재큐잉 건수가 누락 건수와 다릅니다: 누락 " + missing.size() + "건, 재큐잉 " + requeued + "건");
		}
		log.info("reconcile {}: SQS 재큐잉 {}건", targetDate, requeued);

		contribution.incrementFilterCount(sourceObjects.size() - missing.size());
		contribution.incrementWriteCount(requeued);
		return RepeatStatus.FINISHED;
	}

	/** S3에는 있고 Swift에는 없는 객체를 키 순서로 돌려준다. */
	static List<ObjectRef> findMissing(List<ObjectRef> sourceObjects, List<ObjectRef> rawObjects) {
		Set<String> rawKeys = rawObjects.stream()
				.map(ObjectRef::key)
				.collect(Collectors.toSet());
		return sourceObjects.stream()
				.filter(source -> !rawKeys.contains(source.key()))
				.sorted(Comparator.comparing(ObjectRef::key))
				.toList();
	}

}
