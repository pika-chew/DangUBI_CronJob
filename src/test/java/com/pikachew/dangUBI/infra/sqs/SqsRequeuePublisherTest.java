package com.pikachew.dangUBI.infra.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.infra.s3.config.S3Properties;
import com.pikachew.dangUBI.infra.sqs.config.SqsProperties;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 단위 테스트: SQS 재큐잉 구현체. SqsClient는 Mock으로 바꾼다 */
class SqsRequeuePublisherTest {

	private static final String QUEUE_URL = "https://sqs.ap-northeast-2.amazonaws.com/123/ubi-ingest";

	private final SqsClient sqsClient = mock(SqsClient.class);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private final SqsRequeuePublisher publisher = new SqsRequeuePublisher(sqsClient, jsonMapper,
			new SqsProperties(QUEUE_URL, "ap-northeast-2"), new S3Properties("ubi-source", "ap-northeast-2"));

	@Test
	void 열_건씩_나눠_보내고_성공한_건수를_돌려준다() {
		given(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).willAnswer(invocation ->
				allSuccessful(invocation.getArgument(0)));

		int sent = publisher.publish(objects(23));

		assertThat(sent).isEqualTo(23);
		ArgumentCaptor<SendMessageBatchRequest> requests = ArgumentCaptor.forClass(SendMessageBatchRequest.class);
		verify(sqsClient, times(3)).sendMessageBatch(requests.capture());
		assertThat(requests.getAllValues()).extracting(request -> request.entries().size()).containsExactly(10, 10, 3);
		assertThat(requests.getAllValues()).allSatisfy(request -> assertThat(request.queueUrl()).isEqualTo(QUEUE_URL));
	}

	@Test
	void 실패한_메시지는_건수에서_뺀다() {
		given(sqsClient.sendMessageBatch(any(SendMessageBatchRequest.class))).willReturn(SendMessageBatchResponse.builder()
				.successful(SendMessageBatchResultEntry.builder().id("0").build())
				.failed(BatchResultErrorEntry.builder().id("1").code("InternalError").senderFault(false).build())
				.build());

		assertThat(publisher.publish(objects(2))).isEqualTo(1);
	}

	@Test
	void 빈_목록이면_보내지_않는다() {
		assertThat(publisher.publish(List.of())).isZero();
		verify(sqsClient, never()).sendMessageBatch(any(SendMessageBatchRequest.class));
	}

	@Test
	void 메시지는_원본_버킷의_S3_ObjectCreated_이벤트_형식이다() {
		JsonNode record = jsonMapper.readTree(publisher.toS3Event(
				new ObjectRef("firehose/processed/2026/11/08/03/a.jsonl.gz", 120))).get("Records").get(0);

		assertThat(record.get("eventSource").asString()).isEqualTo("aws:s3");
		assertThat(record.get("eventName").asString()).isEqualTo("ObjectCreated:Put");
		assertThat(record.get("awsRegion").asString()).isEqualTo("ap-northeast-2");
		assertThat(record.at("/s3/bucket/name").asString()).isEqualTo("ubi-source");
		assertThat(record.at("/s3/object/key").asString()).isEqualTo("firehose/processed/2026/11/08/03/a.jsonl.gz");
		assertThat(record.at("/s3/object/size").asLong()).isEqualTo(120);
	}

	@Test
	void 키는_경로_구간마다_URL_인코딩한다() {
		JsonNode record = jsonMapper.readTree(publisher.toS3Event(
				new ObjectRef("firehose/processed/2026/11/08/03/a b+c=.jsonl.gz", 1))).get("Records").get(0);

		assertThat(record.at("/s3/object/key").asString()).isEqualTo("firehose/processed/2026/11/08/03/a+b%2Bc%3D.jsonl.gz");
	}

	private static SendMessageBatchResponse allSuccessful(SendMessageBatchRequest request) {
		return SendMessageBatchResponse.builder()
				.successful(request.entries().stream()
						.map(SendMessageBatchRequestEntry::id)
						.map(id -> SendMessageBatchResultEntry.builder().id(id).build())
						.toList())
				.build();
	}

	private static List<ObjectRef> objects(int count) {
		return IntStream.range(0, count)
				.mapToObj(i -> new ObjectRef("firehose/processed/2026/11/08/03/" + i + ".jsonl.gz", i))
				.toList();
	}

}
