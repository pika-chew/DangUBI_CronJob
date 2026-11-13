package com.pikachew.dangUBI.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

	// 수집 적재 오류 (6000번대)
	INGEST_FILE_UNREADABLE(6000, "수집 파일을 읽지 못했습니다."),
	;

	private final Integer code;
	private final String message;

	@Override
	public Reason getReason() {
		return Reason.builder()
				.message(message)
				.code(code)
				.isSuccess(false)
				.build();
	}

}
