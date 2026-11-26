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

	// 설정 (application.yaml)
	public static final String DAILY_PROPERTY_PREFIX = "ubi.batch.daily";

}
