package com.pikachew.dangUBI.batch.daily.config;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_REQUEUE_WAIT_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_PARAM;

import java.time.Clock;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.pikachew.dangUBI.batch.daily.decider.DailyResultDecider;
import com.pikachew.dangUBI.batch.daily.tasklet.RequeueWaitTasklet;

/**
 * dailyJob: 어제(UTC) 하루치를 reconcile → 복사 대기 → ingest → score 순서로 처리한다 (docs/ingest-spec.md 8장).
 *
 * 실행: --spring.batch.job.name=dailyJob   (매일 KST 03:00, K8s CronJob)
 * 파라미터는 받지 않는다. DailyTargetDateIncrementer가 targetDate = 어제(UTC)를 붙인다.
 * 특정 날짜를 돌리려면 --ubi.batch.daily.target-date=2026-11-20을 주거나 개별 Job(reconcileJob, ingestJob, scoreJob)을 쓴다.
 *
 * 1. reconcileStep: S3와 Swift를 비교해 누락 파일을 재큐잉한다. 실패해도 다음 Step으로 넘어간다
 * 2. dailyRequeueWaitStep: S3와 Swift를 다시 비교해 Swift에 없는 파일이 복사될 때까지 기다린다 (최대 requeue-wait-timeout).
 *    S3 조회 실패 등으로 실패해도 있는 Swift 파일은 적재하도록 다음 Step으로 넘어간다
 * 3. ingestFileStep: Swift 파일을 MySQL에 적재한다
 * 4. scoreMetricStep → scoreReportStep: 점수 지표와 하루 리포트·outbox 이벤트를 남긴다
 * 3–4가 실패하면 Job을 멈춘다. 같은 날짜로 다시 띄우면 실패한 Step부터 이어서 처리한다.
 * 1–2가 실패했으면 끝까지 실행한 뒤 Job을 FAILED로 끝낸다 (DailyResultDecider). 이때 실패한 Step은 ABANDONED가 되어
 * 같은 날짜로 다시 띄워도 다시 돌지 않는다. 재큐잉은 reconcileJob을 따로 돌린다.
 *
 * Step은 각 개별 Job의 빈을 그대로 쓴다. targetDate 파라미터 키가 같아 그대로 동작한다.
 */
@Configuration
@EnableConfigurationProperties(DailyProperties.class)
public class DailyJobConfig {

	@Bean(DAILY_JOB_NAME)
	public Job dailyJob(JobRepository jobRepository, DailyProperties dailyProperties, Step reconcileStep,
			Step dailyRequeueWaitStep, Step ingestFileStep, Step scoreMetricStep, Step scoreReportStep) {
		DailyResultDecider resultDecider = new DailyResultDecider();
		return new JobBuilder(DAILY_JOB_NAME, jobRepository)
				.incrementer(new DailyTargetDateIncrementer(Clock.systemUTC(), dailyProperties.targetDate()))
				.validator(new DefaultJobParametersValidator(new String[] {DAILY_TARGET_DATE_PARAM}, new String[0]))
				.start(reconcileStep).on("*").to(dailyRequeueWaitStep)
				.from(dailyRequeueWaitStep).on("*").to(ingestFileStep)
				.from(ingestFileStep).next(scoreMetricStep).next(scoreReportStep)
				.next(resultDecider).on(FlowExecutionStatus.FAILED.getName()).fail()
				.from(resultDecider).on("*").end()
				.end()
				.build();
	}

	/** DB를 쓰지 않고 오래 기다리므로 DB 트랜잭션(커넥션)을 잡지 않는다 */
	@Bean(DAILY_REQUEUE_WAIT_STEP_NAME)
	public Step dailyRequeueWaitStep(JobRepository jobRepository, RequeueWaitTasklet requeueWaitTasklet) {
		return new StepBuilder(DAILY_REQUEUE_WAIT_STEP_NAME, jobRepository)
				.tasklet(requeueWaitTasklet, new ResourcelessTransactionManager())
				.build();
	}

}
