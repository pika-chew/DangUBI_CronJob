package com.pikachew.dangUBI.batch.daily.decider;

import java.util.List;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;

import lombok.extern.slf4j.Slf4j;

/**
 * dailyJob 흐름의 마지막 판정. 앞에서 실패한 Step(reconcile)이 있으면 FAILED를 돌려 Job을 실패로 끝낸다.
 * 흐름이 지나쳐 간 실패 Step은 상태가 ABANDONED로 바뀌고 종료 코드만 FAILED로 남으므로 종료 코드로 판단한다.
 *
 * reconcile이 실패해도 적재·점수는 끝까지 실행하지만, 종료 코드가 0이 아니어야 K8s CronJob이 실패로 보고 알림을 받을 수 있다.
 */
@Slf4j
public class DailyResultDecider implements JobExecutionDecider {

	@Override
	public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {
		List<String> failedSteps = jobExecution.getStepExecutions().stream()
				.filter(step -> ExitStatus.FAILED.getExitCode().equals(step.getExitStatus().getExitCode()))
				.map(StepExecution::getStepName)
				.toList();
		if (failedSteps.isEmpty()) {
			return FlowExecutionStatus.COMPLETED;
		}
		log.warn("daily {}: 실패한 Step이 있어 Job을 FAILED로 끝냄 (뒤 Step은 끝까지 실행함): {}",
				jobExecution.getJobParameters(), failedSteps);
		return FlowExecutionStatus.FAILED;
	}

}
