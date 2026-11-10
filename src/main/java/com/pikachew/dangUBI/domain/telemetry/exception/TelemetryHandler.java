package com.pikachew.dangUBI.domain.telemetry.exception;

import com.pikachew.dangUBI.common.exception.BaseErrorCode;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;

/**
 * 수집 데이터 해석 예외.
 *
 * 상세 정보가 필요 없는 경우는 상수로 쓰고, 어느 필드·값인지 남겨야 하는 경우는 정적 메서드로 만든다.
 */
public class TelemetryHandler extends GeneralException {

	public static final TelemetryHandler INVALID_JSON = new TelemetryHandler(ErrorStatus.TELEMETRY_INVALID_JSON);

	private TelemetryHandler(BaseErrorCode errorCode) {
		super(errorCode);
	}

	private TelemetryHandler(BaseErrorCode errorCode, String detail, Throwable cause) {
		super(errorCode, detail, cause);
	}

	public static TelemetryHandler missingField(String field) {
		return new TelemetryHandler(ErrorStatus.TELEMETRY_MISSING_FIELD, field, null);
	}

	public static TelemetryHandler unsupportedType(int ty) {
		return new TelemetryHandler(ErrorStatus.TELEMETRY_UNSUPPORTED_TYPE, String.valueOf(ty), null);
	}

	public static TelemetryHandler unsupportedSchemaVersion(int schemaVersion) {
		return new TelemetryHandler(ErrorStatus.TELEMETRY_UNSUPPORTED_SCHEMA_VERSION, String.valueOf(schemaVersion), null);
	}

	public static TelemetryHandler fileUnreadable(String objectKey, Throwable cause) {
		return new TelemetryHandler(ErrorStatus.TELEMETRY_FILE_UNREADABLE, objectKey, cause);
	}

}
