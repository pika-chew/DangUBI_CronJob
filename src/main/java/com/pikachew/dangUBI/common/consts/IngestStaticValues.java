package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** ingestJob 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IngestStaticValues {

	/** 이미 저장된 microtrip 키를 조회할 때 IN 절 하나에 넣는 최대 키 수 */
	public static final int IDEMPOTENCY_KEY_QUERY_BATCH_SIZE = 1000;

	/** Reader가 실행 기록(ExecutionContext)에 남기는 "마지막으로 넘긴 파일 키" */
	public static final String READER_LAST_KEY = "ingestFileReader.lastKey";

}
