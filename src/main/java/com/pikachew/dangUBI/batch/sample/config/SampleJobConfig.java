package com.pikachew.dangUBI.batch.sample.config;

import com.pikachew.dangUBI.batch.sample.tasklet.SampleTasklet;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch 설정 확인용 예제 Job. 실제 Job(reconcile, ingest, score 등)을 만들면 지운다.
 *
 * 실행: --spring.batch.job.name=sampleJob targetDate=2026-11-08
 */
@Configuration
public class SampleJobConfig {

	public static final String JOB_NAME = "sampleJob";
	public static final String STEP_NAME = "sampleStep";

	@Bean(JOB_NAME)
	public Job sampleJob(JobRepository jobRepository, Step sampleStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.start(sampleStep)
				.build();
	}

	@Bean(STEP_NAME)
	public Step sampleStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			SampleTasklet sampleTasklet) {
		return new StepBuilder(STEP_NAME, jobRepository)
				.tasklet(sampleTasklet, transactionManager)
				.build();
	}

}
