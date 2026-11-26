package com.pikachew.dangUBI.batch.common.runner;

import static com.pikachew.dangUBI.common.consts.BatchStaticValues.BATCH_JOB_ENABLED_PROPERTY;
import static com.pikachew.dangUBI.common.consts.BatchStaticValues.BATCH_STALE_EXECUTION_RECOVERY_ORDER;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.batch.autoconfigure.BatchProperties;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실행할 Job에 STARTED로 남은 실행을 FAILED로 정리한 뒤 Job을 띄우게 한다.
 *
 * 프로세스가 갑자기 죽으면(kill, OOM, 파드 축출, 노드 다운) Spring Batch가 상태를 기록하지 못해 실행이 STARTED로 남는다.
 * 그대로 두면 같은 파라미터로 다시 띄울 때 JobExecutionAlreadyRunningException으로 막혀 재시작할 수 없다.
 * FAILED로 바꾸면 Spring Batch가 마지막으로 커밋된 chunk 다음부터 이어서 재시작한다.
 *
 * 같은 Job이 동시에 두 개 돌지 않는다는 전제다(운영 CronJob 동시 실행 금지). 이 전제가 깨지면 돌고 있는 실행을 FAILED로 만든다.
 */
@Slf4j
@Component
@ConditionalOnBooleanProperty(name = BATCH_JOB_ENABLED_PROPERTY, matchIfMissing = true)
@RequiredArgsConstructor
public class StaleJobExecutionRecoveryRunner implements ApplicationRunner, Ordered {

	private final BatchProperties batchProperties;
	private final JobRepository jobRepository;
	private final JobOperator jobOperator;

	@Override
	public void run(ApplicationArguments args) {
		String jobName = batchProperties.getJob().getName();
		if (!StringUtils.hasText(jobName)) {
			return;
		}
		for (JobExecution running : jobRepository.findRunningJobExecutions(jobName)) {
			log.warn("이전 실행이 STARTED로 남아 FAILED로 정리한다: job={}, executionId={}, startTime={}, lastUpdated={}, parameters={}",
					jobName, running.getId(), running.getStartTime(), running.getLastUpdated(), running.getJobParameters());
			jobOperator.recover(running);
		}
	}

	@Override
	public int getOrder() {
		return BATCH_STALE_EXECUTION_RECOVERY_ORDER;
	}

}
