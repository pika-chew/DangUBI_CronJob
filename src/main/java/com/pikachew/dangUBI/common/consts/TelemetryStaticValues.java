package com.pikachew.dangUBI.common.consts;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 수집 데이터(저장 경로, 메시지 형식) 관련 고정값. 근거는 docs/ingest-spec.md. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TelemetryStaticValues {

	// 저장 경로 (2장): firehose/processed/{yyyy}/{MM}/{dd}/{HH}/{파일 이름}.gz
	public static final String PROCESSED_ROOT_PREFIX = "firehose/processed/";
	public static final DateTimeFormatter DAY_PATH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd/");
	/** 하루 prefix 뒤에 오는 부분: 시각 폴더(00~23) 바로 아래 .gz 파일 */
	public static final Pattern HOURLY_FILE_PATTERN = Pattern.compile("(?:[01]\\d|2[0-3])/[^/]+\\.gz");

	// 메시지 형식 (5장)
	public static final int TY_TRIP = 3;
	public static final int TY_MICROTRIP = 4;
	public static final int SUPPORTED_SCHEMA_VERSION = 1;
	/** em: 한 주기의 이벤트 비트마스크를 16진 1~2자리로 적은 값 */
	public static final Pattern EVENT_MASK_HEX_PATTERN = Pattern.compile("[0-9A-Fa-f]{1,2}");
	/** xyz: "가속도X,가속도Y,각속도Y" */
	public static final String XYZ_DELIMITER = ",";
	public static final int XYZ_SIZE = 3;

}
