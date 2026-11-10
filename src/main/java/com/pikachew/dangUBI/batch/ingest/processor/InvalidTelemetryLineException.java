package com.pikachew.dangUBI.batch.ingest.processor;

/** 버려야 하는 JSONL 줄. 파일 해석은 이 예외를 잡아 건너뛴 줄로 센다. */
public class InvalidTelemetryLineException extends RuntimeException {

	public InvalidTelemetryLineException(String reason) {
		super(reason);
	}

}
