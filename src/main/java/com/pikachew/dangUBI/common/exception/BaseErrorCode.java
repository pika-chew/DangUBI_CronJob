package com.pikachew.dangUBI.common.exception;

/** 에러 코드 공통 형태. HTTP 응답이 없는 배치라 상태 코드는 두지 않는다. */
public interface BaseErrorCode {

	String getCode();

	String getMessage();

}
