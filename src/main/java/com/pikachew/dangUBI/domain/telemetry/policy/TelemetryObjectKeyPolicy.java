package com.pikachew.dangUBI.domain.telemetry.policy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * 수집 파일의 저장 경로 규칙. S3와 Swift가 같은 키를 쓴다 (docs/ingest-spec.md 2장).
 *
 * <pre>firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.gz   (날짜·시각은 Firehose 수신 시각, UTC)</pre>
 */
@Component
public class TelemetryObjectKeyPolicy {

	public static final String ROOT = "firehose/processed/";

	private static final DateTimeFormatter DAY_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd/");
	private static final Pattern FILE_IN_DAY = Pattern.compile("(?:[01]\\d|2[0-3])/[^/]+\\.gz");

	/** 하루치 파일이 들어 있는 prefix. 예: firehose/processed/2026/11/10/ */
	public String dayPrefix(LocalDate date) {
		return ROOT + date.format(DAY_PATH);
	}

	/** 그날 적재할 파일인지. 시각 폴더(00~23) 바로 아래 .gz 파일만 받는다. */
	public boolean isTelemetryFile(String key, LocalDate date) {
		String prefix = dayPrefix(date);
		return key.startsWith(prefix) && FILE_IN_DAY.matcher(key.substring(prefix.length())).matches();
	}

}
