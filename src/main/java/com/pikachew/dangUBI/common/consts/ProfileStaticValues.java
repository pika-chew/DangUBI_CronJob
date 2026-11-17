package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 실행 프로필 이름 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProfileStaticValues {

	/** 외부 저장소(S3·Swift·SQS) 대신 로컬 디렉터리를 쓰는 개발용 프로필 */
	public static final String LOCAL_PROFILE = "local";
	/** local이 아닐 때. 운영 연동 구현체에 붙인다 */
	public static final String NOT_LOCAL_PROFILE = "!" + LOCAL_PROFILE;

}
