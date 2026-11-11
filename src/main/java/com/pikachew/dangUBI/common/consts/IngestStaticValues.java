package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** ingestJob 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IngestStaticValues {

	// Job·Step 이름
	public static final String JOB_NAME = "ingestJob";
	public static final String STEP_NAME = "ingestFileStep";

	// Job 파라미터
	public static final String TARGET_DATE = "targetDate";
	public static final String RERUN = "rerun";
	/** @StepScope 빈에 targetDate를 주입하는 표현식 */
	public static final String TARGET_DATE_EXPRESSION = "#{jobParameters['" + TARGET_DATE + "']}";

	/** chunk 하나에 담는 수집 파일 수 설정 (application.yaml) */
	public static final String CHUNK_SIZE_PROPERTY = "${ubi.batch.ingest.chunk-size}";

	/** 이미 저장된 microtrip 키를 조회할 때 IN 절 하나에 넣는 최대 키 수 */
	public static final int IDEMPOTENCY_KEY_QUERY_BATCH_SIZE = 1000;

	/** Reader가 실행 기록(ExecutionContext)에 남기는 "마지막으로 넘긴 파일 키" */
	public static final String READER_LAST_KEY = "ingestFileReader.lastKey";

}
