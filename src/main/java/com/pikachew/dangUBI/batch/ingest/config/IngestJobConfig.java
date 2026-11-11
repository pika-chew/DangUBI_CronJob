package com.pikachew.dangUBI.batch.ingest.config;

import static com.pikachew.dangUBI.common.consts.IngestStaticValues.CHUNK_SIZE_PROPERTY;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.JOB_NAME;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.RERUN;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.STEP_NAME;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.TARGET_DATE;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.processor.IngestFileProcessor;
import com.pikachew.dangUBI.batch.ingest.reader.IngestFileReader;
import com.pikachew.dangUBI.batch.ingest.writer.IngestFileWriter;

/**
 * ingestJob: targetDate(UTC) 하루치 Swift 수집 파일을 MySQL에 적재한다 (docs/ingest-spec.md 6장).
 *
 * 실행: --spring.batch.job.name=ingestJob targetDate=2026-11-10
 * 이미 완료된 날짜를 다시 적재: targetDate=2026-11-10 rerun=2 (이전에 쓰지 않은 값)
 * chunk 하나 = 파일 ubi.batch.ingest.chunk-size개. chunk마다 커밋되고, 실패 뒤 같은 파라미터로 다시 실행하면 이어서 처리한다.
 */
@Configuration
public class IngestJobConfig {

	@Bean(JOB_NAME)
	public Job ingestJob(JobRepository jobRepository, Step ingestFileStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.validator(new DefaultJobParametersValidator(new String[] {TARGET_DATE}, new String[] {RERUN}))
				.start(ingestFileStep)
				.build();
	}

	@Bean(STEP_NAME)
	public Step ingestFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			IngestFileReader ingestFileReader, IngestFileProcessor ingestFileProcessor,
			IngestFileWriter ingestFileWriter, @Value(CHUNK_SIZE_PROPERTY) int chunkSize) {
		return new StepBuilder(STEP_NAME, jobRepository)
				.<String, ParsedTelemetryFile>chunk(chunkSize)
				.transactionManager(transactionManager)
				.reader(ingestFileReader)
				.processor(ingestFileProcessor)
				.writer(ingestFileWriter)
				.build();
	}

}
