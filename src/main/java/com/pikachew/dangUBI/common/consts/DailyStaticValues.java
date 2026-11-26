package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** dailyJob 고정값. 하루 실행 순서는 docs/ingest-spec.md 8장. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DailyStaticValues {

	// Job·Step 이름
	public static final String DAILY_JOB_NAME = "dailyJob";
	public static final String DAILY_REQUEUE_WAIT_STEP_NAME = "dailyRequeueWaitStep";

	// Job 파라미터
	/** reconcileStep·ingestFileStep이 읽는 키와 같아야 한다 (RECONCILE_TARGET_DATE_PARAM, INGEST_TARGET_DATE_PARAM) */
	public static final String DAILY_TARGET_DATE_PARAM = "targetDate";
	/** @StepScope 빈에 targetDate를 주입하는 표현식 */
	public static final String DAILY_TARGET_DATE_EXPRESSION = "#{jobParameters['" + DAILY_TARGET_DATE_PARAM + "']}";

	/** 복사되지 않은 키를 경고 로그에 남길 때 앞에서부터 찍는 개수 */
	public static final int DAILY_LOG_KEY_SAMPLE_SIZE = 10;

	// 설정 (application.yaml)
	public static final String DAILY_PROPERTY_PREFIX = "ubi.batch.daily";

}
