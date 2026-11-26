package com.pikachew.dangUBI.batch.daily.tasklet;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_EXPRESSION;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_MISSING_KEYS_CONTEXT;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.daily.config.DailyProperties;
import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;

import lombok.extern.slf4j.Slf4j;

/**
 * dailyJob의 복사 대기 Step: reconcile이 재큐잉한 키가 Swift에 모두 생길 때까지 기다린다.
 *
 * 수집 워커가 SQS 메시지를 받아 비동기로 복사하므로, 바로 적재하면 재큐잉한 파일이 빠진다.
 * 제한 시간이 지나면 남은 키를 경고로 남기고 끝낸다. 적재는 있는 파일로 진행하고, 늦게 복사된 파일은 rerun으로 적재한다.
 * StepExecution 기록: writeCount = 복사가 확인된 키 수, filterCount = 제한 시간까지 복사되지 않은 키 수.
 *
 * DB를 쓰지 않고 오래 기다리므로, Step은 DB 트랜잭션 없이 실행한다 (DailyJobConfig).
 */
@Slf4j
@Component
@StepScope
public class RequeueWaitTasklet implements Tasklet {

	private final RawObjectStore rawObjectStore;
	private final DailyProperties dailyProperties;
	private final LocalDate targetDate;

	public RequeueWaitTasklet(RawObjectStore rawObjectStore, DailyProperties dailyProperties,
			@Value(DAILY_TARGET_DATE_EXPRESSION) String targetDate) {
		this.rawObjectStore = rawObjectStore;
		this.dailyProperties = dailyProperties;
		this.targetDate = LocalDate.parse(targetDate);
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws InterruptedException {
		List<String> waiting = waitingKeys(contribution.getStepExecution().getJobExecution().getExecutionContext());
		if (waiting.isEmpty()) {
			log.info("requeue wait {}: 재큐잉한 파일이 없어 기다리지 않음", targetDate);
			return RepeatStatus.FINISHED;
		}

		long deadline = System.nanoTime() + dailyProperties.requeueWaitTimeout().toNanos();
		while (true) {
			List<String> remaining = notCopied(waiting);
			if (remaining.isEmpty()) {
				log.info("requeue wait {}: 재큐잉한 {}건이 모두 Swift에 복사됨", targetDate, waiting.size());
				contribution.incrementWriteCount(waiting.size());
				return RepeatStatus.FINISHED;
			}
			if (System.nanoTime() >= deadline) {
				log.warn("requeue wait {}: {} 동안 {}건이 복사되지 않아 있는 파일로 적재를 진행함. rerun으로 다시 적재해야 함: {}",
						targetDate, dailyProperties.requeueWaitTimeout(), remaining.size(), remaining);
				contribution.incrementWriteCount(waiting.size() - remaining.size());
				contribution.incrementFilterCount(remaining.size());
				return RepeatStatus.FINISHED;
			}
			sleep(dailyProperties.requeuePollInterval());
		}
	}

	@SuppressWarnings("unchecked")
	private static List<String> waitingKeys(ExecutionContext jobContext) {
		Object keys = jobContext.get(RECONCILE_MISSING_KEYS_CONTEXT);
		return keys == null ? List.of() : (List<String>) keys;
	}

	private List<String> notCopied(List<String> waiting) {
		Set<String> copied = rawObjectStore.listObjects(targetDate).stream()
				.map(ObjectRef::key)
				.collect(Collectors.toSet());
		return waiting.stream().filter(key -> !copied.contains(key)).toList();
	}

	private static void sleep(Duration interval) throws InterruptedException {
		Thread.sleep(interval.toMillis());
	}

}
