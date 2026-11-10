package com.pikachew.dangUBI.common.exception;

import lombok.Getter;

/** 기능별 Handler 예외의 부모. 메시지는 "{에러 코드 메시지}: {상세}" 형태다. */
@Getter
public class GeneralException extends RuntimeException {

	private final BaseErrorCode errorCode;

	public GeneralException(BaseErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public GeneralException(BaseErrorCode errorCode, String detail) {
		this(errorCode, detail, null);
	}

	public GeneralException(BaseErrorCode errorCode, String detail, Throwable cause) {
		super(errorCode.getMessage() + ": " + detail, cause);
		this.errorCode = errorCode;
	}

}
