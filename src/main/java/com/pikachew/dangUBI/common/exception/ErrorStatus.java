package com.pikachew.dangUBI.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

	// TELEMETRY 1xxx: 수집 데이터(trip/microtrip) 해석
	TELEMETRY_INVALID_JSON("TELEMETRY1001", "JSON 객체가 아닙니다"),
	TELEMETRY_MISSING_FIELD("TELEMETRY1002", "키 필드가 없거나 형식이 틀립니다"),
	TELEMETRY_UNSUPPORTED_TYPE("TELEMETRY1003", "지원하지 않는 ty입니다"),
	TELEMETRY_UNSUPPORTED_SCHEMA_VERSION("TELEMETRY1004", "지원하지 않는 schemaVersion입니다"),
	TELEMETRY_FILE_UNREADABLE("TELEMETRY1005", "gz 파일을 읽지 못했습니다"),
	;

	private final String code;
	private final String message;

}
