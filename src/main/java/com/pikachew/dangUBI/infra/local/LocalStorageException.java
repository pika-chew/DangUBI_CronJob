package com.pikachew.dangUBI.infra.local;

/** 로컬 저장소 파일을 읽지 못한 경우의 인프라 예외. local 프로필 전용이라 ErrorStatus로 바꾸지 않고 Step을 실패시킨다 */
public class LocalStorageException extends RuntimeException {

	public LocalStorageException(String message) {
		super(message);
	}

	public LocalStorageException(String message, Throwable cause) {
		super(message, cause);
	}

}
