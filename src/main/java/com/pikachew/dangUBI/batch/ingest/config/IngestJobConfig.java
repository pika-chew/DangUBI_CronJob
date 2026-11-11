package com.pikachew.dangUBI.batch.ingest.config;

import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_CHUNK_SIZE_PROPERTY;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_FILE_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_RERUN_PARAM;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_TARGET_DATE_PARAM;

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
 *
 * ============================================================================================
 * [MEMORY RISK] 처리한 데이터는 별도 저장소 없이 chunk가 커밋될 때까지 JVM 힙에만 있다
 * ============================================================================================
 * - chunk 하나의 최대 메모리 ≈ (가장 큰 파일의 Snapshot 전부 × chunk-size) + (그 chunk에서 저장한 엔티티 전부).
 * - chunk-size가 메모리 사용량을 정하는 가장 큰 손잡이다. 장애 테스트는 운영 최대 파일 크기 기준으로 chunk-size를 정한다.
 * - 위험 지점은 [MEMORY RISK] 주석으로 표시했다: TelemetryJsonlParser, IngestFileWriter, IngestFileReader.
 */
@Configuration
public class IngestJobConfig {

	@Bean(INGEST_JOB_NAME)
	public Job ingestJob(JobRepository jobRepository, Step ingestFileStep) {
		return new JobBuilder(INGEST_JOB_NAME, jobRepository)
				.validator(new DefaultJobParametersValidator(
						new String[] {INGEST_TARGET_DATE_PARAM}, new String[] {INGEST_RERUN_PARAM}))
				.start(ingestFileStep)
				.build();
	}

	@Bean(INGEST_FILE_STEP_NAME)
	public Step ingestFileStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			IngestFileReader ingestFileReader, IngestFileProcessor ingestFileProcessor,
			IngestFileWriter ingestFileWriter, @Value(INGEST_CHUNK_SIZE_PROPERTY) int chunkSize) {
		return new StepBuilder(INGEST_FILE_STEP_NAME, jobRepository)
				.<String, ParsedTelemetryFile>chunk(chunkSize)
				.transactionManager(transactionManager)
				.reader(ingestFileReader)
				.processor(ingestFileProcessor)
				.writer(ingestFileWriter)
				.build();
	}

}
