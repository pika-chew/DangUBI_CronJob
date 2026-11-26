package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** reconcileJob 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReconcileStaticValues {

	// Job·Step 이름
	public static final String RECONCILE_JOB_NAME = "reconcileJob";
	public static final String RECONCILE_STEP_NAME = "reconcileStep";

	// Job 파라미터
	public static final String RECONCILE_TARGET_DATE_PARAM = "targetDate";
	/** @StepScope 빈에 targetDate를 주입하는 표현식 */
	public static final String RECONCILE_TARGET_DATE_EXPRESSION = "#{jobParameters['" + RECONCILE_TARGET_DATE_PARAM + "']}";

}
