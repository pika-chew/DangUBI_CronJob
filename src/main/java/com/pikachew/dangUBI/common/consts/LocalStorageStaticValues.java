package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** local 프로필에서 외부 저장소 대신 쓰는 로컬 디렉터리 관련 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalStorageStaticValues {

	/** 설정 키 prefix. ubi.local-storage.root에 로컬 저장소 루트 경로를 둔다 */
	public static final String LOCAL_STORAGE_PROPERTY_PREFIX = "ubi.local-storage";

	/** 루트 아래 Swift 컨테이너 역할을 하는 디렉터리. 그 아래 상대 경로가 객체 키다 */
	public static final String LOCAL_STORAGE_SWIFT_DIR = "swift";

	/** 루트 아래 S3 원본 버킷 역할을 하는 디렉터리. 키 모양은 swift와 같다 */
	public static final String LOCAL_STORAGE_S3_DIR = "s3";

	/** 객체 키의 경로 구분자. OS와 관계없이 키는 '/'로 나눈다 */
	public static final String LOCAL_STORAGE_KEY_DELIMITER = "/";

}
