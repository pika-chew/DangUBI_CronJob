package com.pikachew.dangUBI.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.infra.s3.config.S3Properties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/** 단위 테스트: S3 원본 버킷 구현체. S3Client는 Mock으로 바꾼다 */
class S3SourceObjectStoreTest {

	private final S3Client s3Client = mock(S3Client.class);
	private final S3SourceObjectStore store = new S3SourceObjectStore(s3Client, new S3Properties("ubi-source", "ap-northeast-2"));

	@Test
	void 그날_prefix로_원본_버킷을_조회해_수집_파일만_돌려준다() {
		given(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).willReturn(ListObjectsV2Response.builder()
				.contents(
						object("firehose/processed/2026/11/08/03/a.jsonl.gz", 120),
						object("firehose/processed/2026/11/08/03/readme.txt", 5),
						object("firehose/processed/2026/11/08/03/nested/b.jsonl.gz", 7))
				.isTruncated(false).build());

		assertThat(store.listObjects(LocalDate.parse("2026-11-08")))
				.containsExactly(new ObjectRef("firehose/processed/2026/11/08/03/a.jsonl.gz", 120));
		ArgumentCaptor<ListObjectsV2Request> request = ArgumentCaptor.forClass(ListObjectsV2Request.class);
		verify(s3Client).listObjectsV2(request.capture());
		assertThat(request.getValue().bucket()).isEqualTo("ubi-source");
		assertThat(request.getValue().prefix()).isEqualTo("firehose/processed/2026/11/08/");
	}

	private static S3Object object(String key, long size) {
		return S3Object.builder().key(key).size(size).build();
	}

}
