package com.pikachew.dangUBI.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

	// 수집 적재 오류 (6000번대)
	INGEST_FILE_UNREADABLE(6000, "수집 파일을 읽지 못했습니다."),

	// 누락 대조 오류 (6100번대)
	RECONCILE_REQUEUE_INCOMPLETE(6100, "누락 객체를 모두 재큐잉하지 못했습니다."),

	// 점수 계산 오류 (6200번대)
	SCORE_UNKNOWN_BASIS(6200, "알 수 없는 ubi.batch.score.basis입니다. application.yaml의 ubi.batch.score.bases에 있는 이름을 쓰세요."),
	;

	private final Integer code;
	private final String message;

	@Override
	public Reason getReason() {
		return Reason.builder()
				.message(message)
				.code(code)
				.isSuccess(false)
				.build();
	}

}
