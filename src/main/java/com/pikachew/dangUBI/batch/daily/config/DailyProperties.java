package com.pikachew.dangUBI.batch.daily.config;

import static com.pikachew.dangUBI.common.consts.DailyStaticValues.DAILY_PROPERTY_PREFIX;

import java.time.Duration;
import java.time.LocalDate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * dailyJob 설정 (docs/ingest-spec.md 8장).
 *
 * @param requeueWaitTimeout  재큐잉한 파일이 Swift에 복사되기를 기다리는 최대 시간. 지나면 있는 파일만 적재한다
 * @param requeuePollInterval Swift 목록을 다시 확인하는 간격
 * @param targetDate          처리할 날짜(UTC). 비우면 실행 시각 기준 어제(UTC)다. 로컬 시연·수동 실행용
 */
@ConfigurationProperties(DAILY_PROPERTY_PREFIX)
public record DailyProperties(
		Duration requeueWaitTimeout,
		Duration requeuePollInterval,
		LocalDate targetDate
) {
}
