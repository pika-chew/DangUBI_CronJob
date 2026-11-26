package com.pikachew.dangUBI.batch.daily.tasklet;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_LOG_KEY_SAMPLE_SIZE;
import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_EXPRESSION;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.daily.config.DailyProperties;
import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.batch.reconcile.tasklet.ReconcileTasklet;

import lombok.extern.slf4j.Slf4j;

/**
 * dailyJob의 복사 대기 Step: S3에는 있는데 Swift에 없는 파일이 없어질 때까지 기다린다.
 *
 * 수집 워커가 재큐잉 메시지를 받아 비동기로 복사하므로, 바로 적재하면 재큐잉한 파일이 빠진다.
 * 기다릴 키를 Job 실행 기록에 넘겨받지 않고 S3·Swift 목록을 직접 비교한다. 실행 기록 컬럼(MySQL TEXT, 64KB)은
 * 키 수백 건이면 넘쳐 Job이 멈추고, 누락이 많은 날일수록 이 Step이 필요하기 때문이다.
 * S3 목록은 처음에 한 번 읽고, Swift 목록만 다시 확인한다.
 *
 * 제한 시간이 지나면 남은 키 수와 일부를 경고로 남기고 끝낸다. 적재는 있는 파일로 진행하고, 늦게 복사된 파일은 rerun으로 적재한다.
 * StepExecution 기록: writeCount = 기다리는 동안 복사가 확인된 키 수, filterCount = 제한 시간까지 복사되지 않은 키 수.
 *
 * DB를 쓰지 않고 오래 기다리므로, Step은 DB 트랜잭션 없이 실행한다 (DailyJobConfig).
 */
@Slf4j
@Component
@StepScope
public class RequeueWaitTasklet implements Tasklet {

	private final SourceObjectStore sourceObjectStore;
	private final RawObjectStore rawObjectStore;
	private final DailyProperties dailyProperties;
	private final LocalDate targetDate;

	public RequeueWaitTasklet(SourceObjectStore sourceObjectStore, RawObjectStore rawObjectStore,
			DailyProperties dailyProperties, @Value(DAILY_TARGET_DATE_EXPRESSION) String targetDate) {
		this.sourceObjectStore = sourceObjectStore;
		this.rawObjectStore = rawObjectStore;
		this.dailyProperties = dailyProperties;
		this.targetDate = LocalDate.parse(targetDate);
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws InterruptedException {
		List<ObjectRef> sourceObjects = sourceObjectStore.listObjects(targetDate);
		List<ObjectRef> waiting = notCopied(sourceObjects);
		if (waiting.isEmpty()) {
			log.info("requeue wait {}: Swift에 없는 파일이 없어 기다리지 않음", targetDate);
			return RepeatStatus.FINISHED;
		}
		log.info("requeue wait {}: Swift에 없는 {}건이 복사되기를 기다림 (최대 {})",
				targetDate, waiting.size(), dailyProperties.requeueWaitTimeout());

		long deadline = System.nanoTime() + dailyProperties.requeueWaitTimeout().toNanos();
		while (true) {
			sleep(dailyProperties.requeuePollInterval());
			List<ObjectRef> remaining = notCopied(waiting);
			if (remaining.isEmpty()) {
				log.info("requeue wait {}: {}건이 모두 Swift에 복사됨", targetDate, waiting.size());
				contribution.incrementWriteCount(waiting.size());
				return RepeatStatus.FINISHED;
			}
			if (System.nanoTime() >= deadline) {
				log.warn("requeue wait {}: {} 동안 {}건이 복사되지 않아 있는 파일로 적재를 진행함. 복사된 뒤 rerun으로 다시 적재해야 함. 일부: {}",
						targetDate, dailyProperties.requeueWaitTimeout(), remaining.size(), sample(remaining));
				contribution.incrementWriteCount(waiting.size() - remaining.size());
				contribution.incrementFilterCount(remaining.size());
				return RepeatStatus.FINISHED;
			}
		}
	}

	private List<ObjectRef> notCopied(List<ObjectRef> objects) {
		return ReconcileTasklet.findMissing(objects, rawObjectStore.listObjects(targetDate));
	}

	/** 수백 건을 모두 찍으면 로그가 너무 길어 앞부분만 남긴다 */
	private static List<String> sample(List<ObjectRef> objects) {
		return objects.stream().limit(DAILY_LOG_KEY_SAMPLE_SIZE).map(ObjectRef::key).toList();
	}

	private static void sleep(Duration interval) throws InterruptedException {
		Thread.sleep(interval.toMillis());
	}

}
