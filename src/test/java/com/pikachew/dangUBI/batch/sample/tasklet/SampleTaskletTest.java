package com.pikachew.dangUBI.batch.sample.tasklet;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

/** 단위 테스트: 스프링 컨텍스트·DB 없이 Tasklet만 검증한다. */
class SampleTaskletTest {

	@Test
	void 한_번_실행하면_FINISHED를_반환한다() {
		SampleTasklet tasklet = new SampleTasklet("2026-11-08");
		StepContribution contribution = MetaDataInstanceFactory.createStepExecution().createStepContribution();

		RepeatStatus status = tasklet.execute(contribution, null);

		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
	}

}
