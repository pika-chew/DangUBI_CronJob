package com.pikachew.dangUBI.infra.sqs;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.OBJECT_STORAGE_KEY_DELIMITER;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;
import static com.pikachew.dangUBI.common.consts.SqsStaticValues.SQS_REQUEUE_EVENT_NAME;
import static com.pikachew.dangUBI.common.consts.SqsStaticValues.SQS_REQUEUE_EVENT_SOURCE;
import static com.pikachew.dangUBI.common.consts.SqsStaticValues.SQS_REQUEUE_EVENT_VERSION;
import static com.pikachew.dangUBI.common.consts.SqsStaticValues.SQS_SEND_BATCH_MAX_SIZE;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;
import com.pikachew.dangUBI.infra.s3.config.S3Properties;
import com.pikachew.dangUBI.infra.sqs.config.SqsProperties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 누락 객체를 수집 워커의 SQS 큐에 다시 넣는다. 메시지는 S3 ObjectCreated 이벤트 알림 형식이라
 * 수집 워커는 Firehose가 쓴 파일과 똑같이 처리한다 (docs/ingest-spec.md 4장).
 *
 * 일부 메시지가 실패하면 성공한 건수만 돌려준다. ReconcileTasklet이 건수가 모자라면 Step을 실패시키고,
 * 다시 돌면 이미 들어간 객체도 한 번 더 보내지만 수집 워커가 덮어쓰기라 안전하다.
 *
 * TODO: 수집 워커가 실제로 읽는 필드를 수집 워커 담당과 확인한다. 지금은 S3 이벤트의 최소 필드만 넣는다.
 */
@Slf4j
@Component
@Profile(NOT_LOCAL_PROFILE)
public class SqsRequeuePublisher implements RequeuePublisher {

	private final SqsClient sqsClient;
	private final JsonMapper jsonMapper;
	private final String queueUrl;
	private final String sourceBucket;
	private final String sourceRegion;

	public SqsRequeuePublisher(SqsClient sqsClient, JsonMapper jsonMapper, SqsProperties sqsProperties,
			S3Properties s3Properties) {
		this.sqsClient = sqsClient;
		this.jsonMapper = jsonMapper;
		this.queueUrl = sqsProperties.queueUrl();
		this.sourceBucket = s3Properties.bucket();
		this.sourceRegion = s3Properties.region();
	}

	@Override
	public int publish(List<ObjectRef> missingObjects) {
		int sent = 0;
		for (int from = 0; from < missingObjects.size(); from += SQS_SEND_BATCH_MAX_SIZE) {
			sent += send(missingObjects.subList(from, Math.min(from + SQS_SEND_BATCH_MAX_SIZE, missingObjects.size())));
		}
		return sent;
	}

	private int send(List<ObjectRef> batch) {
		List<SendMessageBatchRequestEntry> entries = IntStream.range(0, batch.size())
				.mapToObj(i -> SendMessageBatchRequestEntry.builder()
						.id(String.valueOf(i))
						.messageBody(toS3Event(batch.get(i)))
						.build())
				.toList();
		SendMessageBatchResponse response = sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
				.queueUrl(queueUrl)
				.entries(entries)
				.build());
		response.failed().forEach(failure -> log.warn("재큐잉 실패: {} ({}: {})",
				batch.get(Integer.parseInt(failure.id())).key(), failure.code(), failure.message()));
		return response.successful().size();
	}

	String toS3Event(ObjectRef object) {
		ObjectNode record = jsonMapper.createObjectNode()
				.put("eventVersion", SQS_REQUEUE_EVENT_VERSION)
				.put("eventSource", SQS_REQUEUE_EVENT_SOURCE)
				.put("awsRegion", sourceRegion)
				.put("eventName", SQS_REQUEUE_EVENT_NAME);
		ObjectNode s3 = record.putObject("s3");
		s3.putObject("bucket").put("name", sourceBucket);
		s3.putObject("object").put("key", encodeKey(object.key())).put("size", object.size());

		ObjectNode event = jsonMapper.createObjectNode();
		event.putArray("Records").add(record);
		return jsonMapper.writeValueAsString(event);
	}

	/** S3 이벤트처럼 경로 구간마다 URL 인코딩한다('/'는 그대로, 공백은 '+') */
	private static String encodeKey(String key) {
		return Arrays.stream(key.split(OBJECT_STORAGE_KEY_DELIMITER, -1))
				.map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8))
				.collect(Collectors.joining(OBJECT_STORAGE_KEY_DELIMITER));
	}

}
