package com.pikachew.dangUBI.infra.swift;

/**
 * JSONL 한 줄을 해석할 수 없는 경우. 파일 해석은 이 예외를 잡아 그 줄을 건너뛰고 수만 센다.
 * 메시지에 버린 이유(없는 필드, 지원하지 않는 ty 등)가 들어간다.
 */
public class InvalidTelemetryLineException extends RuntimeException {

	public InvalidTelemetryLineException(String message) {
		super(message);
	}

}
