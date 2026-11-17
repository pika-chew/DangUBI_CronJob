package com.pikachew.dangUBI.batch.reconcile.config;

import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ReconcileStaticValues.RECONCILE_TARGET_DATE_PARAM;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.pikachew.dangUBI.batch.reconcile.tasklet.ReconcileTasklet;

/**
 * reconcileJob: targetDate의 S3 원본 목록과 Swift 목록을 비교해, Swift에 없는 객체를 SQS로 다시 넣는다. 직접 복사하지 않는다.
 *
 * 실행: --spring.batch.job.name=reconcileJob targetDate=2026-11-08
 */
@Configuration
public class ReconcileJobConfig {

	@Bean(RECONCILE_JOB_NAME)
	public Job reconcileJob(JobRepository jobRepository, Step reconcileStep) {
		return new JobBuilder(RECONCILE_JOB_NAME, jobRepository)
				.validator(new DefaultJobParametersValidator(new String[] {RECONCILE_TARGET_DATE_PARAM}, new String[0]))
				.start(reconcileStep)
				.build();
	}

	@Bean(RECONCILE_STEP_NAME)
	public Step reconcileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ReconcileTasklet reconcileTasklet) {
		return new StepBuilder(RECONCILE_STEP_NAME, jobRepository)
				.tasklet(reconcileTasklet, transactionManager)
				.build();
	}

}
