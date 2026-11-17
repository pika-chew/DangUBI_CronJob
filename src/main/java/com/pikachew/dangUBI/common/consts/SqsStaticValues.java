package com.pikachew.dangUBI.common.consts;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 재큐잉(SQS) 관련 고정값 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqsStaticValues {

	public static final String SQS_PROPERTY_PREFIX = "ubi.sqs";
	/** SendMessageBatch 한 번에 보낼 수 있는 최대 건수 (SQS 한도) */
	public static final int SQS_SEND_BATCH_MAX_SIZE = 10;

	// 재큐잉 메시지: 수집 워커가 받는 S3 이벤트 알림 형식 (docs/ingest-spec.md 4장)
	public static final String SQS_REQUEUE_EVENT_VERSION = "2.1";
	public static final String SQS_REQUEUE_EVENT_SOURCE = "aws:s3";
	public static final String SQS_REQUEUE_EVENT_NAME = "ObjectCreated:Put";

}
