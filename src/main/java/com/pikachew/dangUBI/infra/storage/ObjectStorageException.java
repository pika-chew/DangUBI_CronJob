package com.pikachew.dangUBI.infra.storage;

/**
 * S3 호환 저장소(S3 원본, Swift s3api) 호출이 실패한 경우의 인프라 예외. 어느 버킷·키에서 실패했는지 메시지에 담는다.
 * 일시적인 장애일 수 있으므로 Step을 실패시키고 같은 파라미터로 다시 실행한다.
 */
public class ObjectStorageException extends RuntimeException {

	public ObjectStorageException(String message, Throwable cause) {
		super(message, cause);
	}

}
