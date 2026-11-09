package com.pikachew.dangUBI.batch.sample.tasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class SampleTasklet implements Tasklet {

	private final String targetDate;

	public SampleTasklet(@Value("#{jobParameters['targetDate']}") String targetDate) {
		this.targetDate = targetDate;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		log.info("sampleJob 실행: targetDate={}", targetDate);
		return RepeatStatus.FINISHED;
	}

}
