package com.pikachew.dangUBI.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 단위 테스트: S3 API로 버킷을 읽는 공용 클래스. S3Client는 Mock으로 바꾼다 */
class S3CompatibleBucketTest {

	private static final String PREFIX = "firehose/processed/2026/11/08/";

	private final S3Client s3Client = mock(S3Client.class);
	private final S3CompatibleBucket bucket = new S3CompatibleBucket(s3Client, "raw");

	@Test
	void 페이지를_끝까지_넘기며_목록을_모은다() {
		given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(
				ListObjectsV2Response.builder()
						.contents(object(PREFIX + "03/a.jsonl.gz", 10))
						.isTruncated(true).nextContinuationToken("token-1").build(),
				ListObjectsV2Response.builder()
						.contents(object(PREFIX + "14/b.jsonl.gz", 20))
						.isTruncated(false).build());

		List<StorageObject> objects = bucket.list(PREFIX);

		assertThat(objects).containsExactly(
				new StorageObject(PREFIX + "03/a.jsonl.gz", 10),
				new StorageObject(PREFIX + "14/b.jsonl.gz", 20));
		ArgumentCaptor<ListObjectsV2Request> requests = ArgumentCaptor.forClass(ListObjectsV2Request.class);
		verify(s3Client, times(2)).listObjectsV2(requests.capture());
		assertThat(requests.getAllValues()).allSatisfy(request -> {
			assertThat(request.bucket()).isEqualTo("raw");
			assertThat(request.prefix()).isEqualTo(PREFIX);
		});
		assertThat(requests.getAllValues().get(0).continuationToken()).isNull();
		assertThat(requests.getAllValues().get(1).continuationToken()).isEqualTo("token-1");
	}

	@Test
	void 슬래시로_끝나는_폴더_표시_객체는_뺀다() {
		given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(
				ListObjectsV2Response.builder()
						.contents(object(PREFIX + "03/", 0), object(PREFIX + "03/a.jsonl.gz", 10))
						.isTruncated(false).build());

		assertThat(bucket.list(PREFIX)).containsExactly(new StorageObject(PREFIX + "03/a.jsonl.gz", 10));
	}

	@Test
	void 목록_조회가_실패하면_버킷과_prefix를_담은_ObjectStorageException을_던진다() {
		given(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
				.willThrow(S3Exception.builder().message("Access Denied").statusCode(403).build());

		assertThatThrownBy(() -> bucket.list(PREFIX))
				.isInstanceOf(ObjectStorageException.class)
				.hasMessageContaining("raw/" + PREFIX);
	}

	@Test
	void 키로_객체_내용을_연다() throws IOException {
		given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(new ResponseInputStream<>(
				GetObjectResponse.builder().build(),
				AbortableInputStream.create(new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)))));

		try (InputStream in = bucket.open(PREFIX + "03/a.jsonl.gz")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
		}
		ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
		verify(s3Client).getObject(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("raw");
		assertThat(request.getValue().key()).isEqualTo(PREFIX + "03/a.jsonl.gz");
	}

	@Test
	void 없는_객체를_열면_ObjectStorageException을_던진다() {
		given(s3Client.getObject(any(GetObjectRequest.class)))
				.willThrow(NoSuchKeyException.builder().message("no such key").build());

		assertThatThrownBy(() -> bucket.open(PREFIX + "03/none.jsonl.gz"))
				.isInstanceOf(ObjectStorageException.class)
				.hasMessageContaining("raw/" + PREFIX + "03/none.jsonl.gz");
	}

	private static S3Object object(String key, long size) {
		return S3Object.builder().key(key).size(size).build();
	}

}
