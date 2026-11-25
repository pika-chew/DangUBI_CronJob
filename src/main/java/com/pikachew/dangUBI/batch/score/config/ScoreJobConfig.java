package com.pikachew.dangUBI.batch.score.config;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_BASIS_PARAM;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_CHUNK_SIZE_PROPERTY;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_JOB_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_METRIC_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_REPORT_STEP_NAME;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_RUN_ID_PARAM;

import java.util.List;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.CompositeJobParametersValidator;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParametersValidator;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.pikachew.dangUBI.batch.score.processor.TripScoreMetricProcessor;
import com.pikachew.dangUBI.batch.score.reader.ScoreDriverReader;
import com.pikachew.dangUBI.batch.score.reader.ScoreTripReader;
import com.pikachew.dangUBI.batch.score.writer.ScoreReportWriter;
import com.pikachew.dangUBI.batch.score.writer.TripScoreMetricWriter;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;

/**
 * scoreJob: 종료된 trip의 점수 지표를 만들고, 운전자별 구간 감점과 하루 리포트를 남긴다 (docs/score-spec.md).
 *
 * 실행: --spring.batch.job.name=scoreJob            (운영 기준: 구간 125km, 반영 범위 1,500km)
 *       --spring.batch.job.name=scoreJob scoreBasis=test  (구간 50km, 반영 범위 600km. 테스트 DB에서만)
 * RunIdIncrementer가 run.id를 붙여 파라미터 없이 매일 새 실행을 만든다.
 *
 * 1. scoreMetricStep (chunk = trip): 아직 지표가 없는 COMPLETED trip마다 지표를 만든다.
 * 2. scoreReportStep (chunk = 운전자): 지표가 새로 생긴 운전자마다 구간을 닫고 하루 리포트·outbox 이벤트를 남긴다.
 * 처리한 것은 지표·구간·리포트가 채워져 다시 잡히지 않으므로, 실패 뒤 다시 실행하면 남은 것만 처리한다.
 */
@Configuration
@EnableConfigurationProperties(ScoreProperties.class)
public class ScoreJobConfig {

	@Bean(SCORE_JOB_NAME)
	public Job scoreJob(JobRepository jobRepository, Step scoreMetricStep, Step scoreReportStep, ScoreProperties scoreProperties) {
		return new JobBuilder(SCORE_JOB_NAME, jobRepository)
				.incrementer(new RunIdIncrementer())
				.validator(validator(scoreProperties))
				.start(scoreMetricStep)
				.next(scoreReportStep)
				.build();
	}

	@Bean(SCORE_METRIC_STEP_NAME)
	public Step scoreMetricStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ScoreTripReader scoreTripReader, TripScoreMetricProcessor tripScoreMetricProcessor,
			TripScoreMetricWriter tripScoreMetricWriter, @Value(SCORE_CHUNK_SIZE_PROPERTY) int chunkSize) {
		return new StepBuilder(SCORE_METRIC_STEP_NAME, jobRepository)
				.<Long, TripScoreMetric>chunk(chunkSize)
				.transactionManager(transactionManager)
				.reader(scoreTripReader)
				.processor(tripScoreMetricProcessor)
				.writer(tripScoreMetricWriter)
				.build();
	}

	@Bean(SCORE_REPORT_STEP_NAME)
	public Step scoreReportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ScoreDriverReader scoreDriverReader, ScoreReportWriter scoreReportWriter,
			@Value(SCORE_CHUNK_SIZE_PROPERTY) int chunkSize) {
		return new StepBuilder(SCORE_REPORT_STEP_NAME, jobRepository)
				.<Long, Long>chunk(chunkSize)
				.transactionManager(transactionManager)
				.reader(scoreDriverReader)
				.writer(scoreReportWriter)
				.build();
	}

	/** 허용한 파라미터만 받고, scoreBasis는 설정에 있는 이름인지 Step을 돌리기 전에 확인한다 */
	private static JobParametersValidator validator(ScoreProperties scoreProperties) {
		JobParametersValidator basisValidator = parameters -> {
			try {
				scoreProperties.basis(parameters.getString(SCORE_BASIS_PARAM));
			} catch (GeneralException e) {
				throw new InvalidJobParametersException(e.getMessage());
			}
		};
		CompositeJobParametersValidator validator = new CompositeJobParametersValidator();
		validator.setValidators(List.of(
				new DefaultJobParametersValidator(new String[0], new String[] {SCORE_BASIS_PARAM, SCORE_RUN_ID_PARAM}),
				basisValidator));
		return validator;
	}

}
