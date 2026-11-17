package com.pikachew.dangUBI.infra.storage;

import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_DAY_PATH_FORMAT;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_HOURLY_FILE_PATTERN;
import static com.pikachew.dangUBI.common.consts.TelemetryStaticValues.TELEMETRY_PROCESSED_ROOT_PREFIX;

import java.time.LocalDate;

/**
 * 수집 파일의 저장 경로 규칙. S3와 Swift가 같은 키를 쓴다 (docs/ingest-spec.md 2장).
 *
 * <pre>firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.gz   (날짜·시각은 Firehose 수신 시각, UTC)</pre>
 */
public final class TelemetryObjectKeys {

	private TelemetryObjectKeys() {
	}

	/** 하루치 파일이 들어 있는 prefix. 예: firehose/processed/2026/11/10/ */
	public static String dayPrefix(LocalDate date) {
		return TELEMETRY_PROCESSED_ROOT_PREFIX + date.format(TELEMETRY_DAY_PATH_FORMAT);
	}

	/** 그날 적재할 파일인지. 시각 폴더(00~23) 바로 아래 .gz 파일만 받는다 */
	public static boolean isTelemetryFile(String key, LocalDate date) {
		String prefix = dayPrefix(date);
		return key.startsWith(prefix) && TELEMETRY_HOURLY_FILE_PATTERN.matcher(key.substring(prefix.length())).matches();
	}

}
