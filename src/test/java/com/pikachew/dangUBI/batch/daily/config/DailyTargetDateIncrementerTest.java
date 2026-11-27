package com.pikachew.dangUBI.batch.daily.config;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_TARGET_DATE_PARAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/** 단위 테스트: dailyJob의 targetDate = 어제(UTC) */
class DailyTargetDateIncrementerTest {

	@Test
	void UTC_02시에_띄우면_targetDate는_어제_UTC다() {
		// 2026-11-26T02:00Z = KST 11:00
		Clock clock = Clock.fixed(Instant.parse("2026-11-26T02:00:00Z"), ZoneOffset.UTC);

		JobParameters next = new DailyTargetDateIncrementer(clock, null).getNext(new JobParameters());

		assertThat(next.getString(DAILY_TARGET_DATE_PARAM)).isEqualTo("2026-11-25");
	}

	@Test
	void KST_03시에_띄우면_targetDate는_이틀_전_UTC_하루다() {
		// KST 11/28 03:00 = UTC 11/27 18:00이라 어제(UTC)는 11/26이다
		Clock clock = Clock.fixed(Instant.parse("2026-11-27T18:00:00Z"), ZoneOffset.UTC);

		JobParameters next = new DailyTargetDateIncrementer(clock, null).getNext(new JobParameters());

		assertThat(next.getString(DAILY_TARGET_DATE_PARAM)).isEqualTo("2026-11-26");
	}

	@Test
	void 서버_시간대가_KST여도_UTC_날짜로_정한다() {
		// KST로는 11/26 08:59지만 UTC로는 11/25 23:59라 어제(UTC)는 11/24다
		Clock clock = Clock.fixed(Instant.parse("2026-11-25T23:59:00Z"), ZoneOffset.UTC);

		assertThat(new DailyTargetDateIncrementer(clock, null).getNext(new JobParameters()).getString(DAILY_TARGET_DATE_PARAM))
				.isEqualTo("2026-11-24");
	}

	@Test
	void 지정한_날짜가_있으면_그_날짜를_쓴다() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-26T02:00:00Z"), ZoneOffset.UTC);

		JobParameters next = new DailyTargetDateIncrementer(clock, LocalDate.parse("2026-11-20")).getNext(new JobParameters());

		assertThat(next.getString(DAILY_TARGET_DATE_PARAM)).isEqualTo("2026-11-20");
	}

	@Test
	void 이전_실행의_파라미터는_보지_않는다() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-26T02:00:00Z"), ZoneOffset.UTC);
		JobParameters previous = new JobParametersBuilder().addString(DAILY_TARGET_DATE_PARAM, "2026-01-01").toJobParameters();

		JobParameters next = new DailyTargetDateIncrementer(clock, null).getNext(previous);

		assertThat(next.getString(DAILY_TARGET_DATE_PARAM)).isEqualTo("2026-11-25");
		assertThat(next.parameters()).hasSize(1);
	}

}
