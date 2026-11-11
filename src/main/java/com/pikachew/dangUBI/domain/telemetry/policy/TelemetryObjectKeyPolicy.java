package com.pikachew.dangUBI.domain.telemetry.policy;

import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.DAY_PATH_FORMAT;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.HOURLY_FILE_PATTERN;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.PROCESSED_ROOT_PREFIX;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

/**
 * 수집 파일의 저장 경로 규칙. S3와 Swift가 같은 키를 쓴다 (docs/ingest-spec.md 2장).
 *
 * <pre>firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.gz   (날짜·시각은 Firehose 수신 시각, UTC)</pre>
 */
@Component
public class TelemetryObjectKeyPolicy {

	/** 하루치 파일이 들어 있는 prefix. 예: firehose/processed/2026/11/10/ */
	public String dayPrefix(LocalDate date) {
		return PROCESSED_ROOT_PREFIX + date.format(DAY_PATH_FORMAT);
	}

	/** 그날 적재할 파일인지. 시각 폴더(00~23) 바로 아래 .gz 파일만 받는다. */
	public boolean isTelemetryFile(String key, LocalDate date) {
		String prefix = dayPrefix(date);
		return key.startsWith(prefix) && HOURLY_FILE_PATTERN.matcher(key.substring(prefix.length())).matches();
	}

}
