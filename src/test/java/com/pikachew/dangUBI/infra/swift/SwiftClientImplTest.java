package com.pikachew.dangUBI.infra.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pikachew.dangUBI.infra.swift.config.SwiftProperties;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 단위 테스트: Swift s3api 구현체. S3Client는 Mock으로 바꾼다. 페이지 넘김은 S3CompatibleBucketTest에서 본다 */
class SwiftClientImplTest {

	private final S3Client s3Client = mock(S3Client.class);
	private final SwiftClientImpl client = new SwiftClientImpl(s3Client,
			new SwiftProperties("http://swift.test", "ubi-raw", "access", "secret", "us-east-1"));

	@Test
	void 설정한_컨테이너에서_목록을_읽어_Swift_객체로_돌려준다() {
		given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(ListObjectsV2Response.builder()
				.contents(S3Object.builder().key("firehose/processed/2026/11/08/03/a.jsonl.gz").size(10L).build())
				.isTruncated(false).build());

		assertThat(client.listObjects("firehose/processed/2026/11/08/"))
				.containsExactly(new SwiftObject("firehose/processed/2026/11/08/03/a.jsonl.gz", 10));
		ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
		verify(s3Client).listObjectsV2(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("ubi-raw");
	}

	@Test
	void 설정한_컨테이너의_객체를_연다() throws IOException {
		given(s3Client.getObject(any(GetObjectRequest.class))).willReturn(new ResponseInputStream<>(
				GetObjectResponse.builder().build(), AbortableInputStream.create(new ByteArrayInputStream(new byte[] {1, 2}))));

		try (InputStream in = client.download("firehose/processed/2026/11/08/03/a.jsonl.gz")) {
			assertThat(in.readAllBytes()).containsExactly(1, 2);
		}
		ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
		verify(s3Client).getObject(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("ubi-raw");
	}

}
