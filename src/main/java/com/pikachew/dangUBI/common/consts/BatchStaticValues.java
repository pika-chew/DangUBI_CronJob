package com.pikachew.dangUBI.common.consts;

import org.springframework.boot.batch.autoconfigure.JobLauncherApplicationRunner;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 여러 Job이 같이 쓰는 실행 고정값. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchStaticValues {

	// 설정 (application.yaml)
	public static final String BATCH_JOB_ENABLED_PROPERTY = "spring.batch.job.enabled";

	/** 죽은 실행 정리는 Job을 띄우기 전에 끝나야 한다 */
	public static final int BATCH_STALE_EXECUTION_RECOVERY_ORDER = JobLauncherApplicationRunner.DEFAULT_ORDER - 1;

}
