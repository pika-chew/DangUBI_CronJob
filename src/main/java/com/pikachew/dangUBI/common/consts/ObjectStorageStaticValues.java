package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** S3 호환 객체 저장소(S3 원본 버킷, Swift s3api) 접근 관련 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ObjectStorageStaticValues {

	/** 키의 경로 구분자. 이 문자로 끝나는 키는 폴더 표시 객체라 목록에서 뺀다 */
	public static final String OBJECT_STORAGE_KEY_DELIMITER = "/";

}
