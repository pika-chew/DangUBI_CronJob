package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** S3 호환 객체 저장소(S3 원본 버킷, Swift s3api) 접근 관련 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ObjectStorageStaticValues {

	/** 키의 경로 구분자. 이 문자로 끝나는 키는 폴더 표시 객체라 목록에서 뺀다 */
	public static final String OBJECT_STORAGE_KEY_DELIMITER = "/";

	// S3 원본 버킷 (AWS)
	/** 설정 키 prefix. ubi.s3.bucket, region */
	public static final String S3_PROPERTY_PREFIX = "ubi.s3";
	/** AWS S3 원본 버킷에 붙는 S3Client 빈 이름. Swift용 S3Client와 구분한다 */
	public static final String S3_SOURCE_CLIENT_BEAN = "sourceS3Client";

	// Swift (s3api)
	/** 설정 키 prefix. ubi.swift.endpoint, container, access-key, secret-key, region */
	public static final String SWIFT_PROPERTY_PREFIX = "ubi.swift";
	/** Swift s3api에 붙는 S3Client 빈 이름. S3 원본용 S3Client와 구분한다 */
	public static final String SWIFT_S3_CLIENT_BEAN = "swiftS3Client";

}
