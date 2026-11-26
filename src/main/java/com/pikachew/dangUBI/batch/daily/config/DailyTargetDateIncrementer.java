package com.pikachew.dangUBI.batch.daily.config;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_PARAM;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.JobParametersIncrementer;

/**
 * dailyJob을 파라미터 없이 띄우면 targetDate = 어제(UTC)를 붙인다. 지정한 날짜가 있으면 그 날짜를 쓴다.
 *
 * 날짜가 같으면 같은 실행 단위(JobInstance)라, 실패한 날을 다시 띄우면 실패한 Step부터 이어서 처리하고
 * 이미 끝난 날을 다시 띄우면 실행되지 않는다. 이전 실행의 파라미터는 보지 않는다.
 */
public class DailyTargetDateIncrementer implements JobParametersIncrementer {

	private final Clock clock;
	private final LocalDate fixedTargetDate;

	public DailyTargetDateIncrementer(Clock clock, LocalDate fixedTargetDate) {
		this.clock = clock;
		this.fixedTargetDate = fixedTargetDate;
	}

	@Override
	public JobParameters getNext(JobParameters parameters) {
		LocalDate targetDate = fixedTargetDate != null ? fixedTargetDate : LocalDate.now(clock).minusDays(1);
		return new JobParametersBuilder().addString(DAILY_TARGET_DATE_PARAM, targetDate.toString()).toJobParameters();
	}

}
