package com.pikachew.dangUBI.infra.swift;

/**
 * 수집 파일 자체를 읽을 수 없는 경우(gz가 깨졌거나 빈 파일)를 나타내는 인프라 예외.
 * Processor에서 ErrorStatus.INGEST_FILE_UNREADABLE로 변환한다.
 */
public class TelemetryFileFormatException extends RuntimeException {

	public TelemetryFileFormatException(String message, Throwable cause) {
		super(message, cause);
	}

}
