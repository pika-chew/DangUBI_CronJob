package com.pikachew.dangUBI.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.infra.s3.S3SourceObjectStore;
import com.pikachew.dangUBI.infra.sqs.SqsRequeuePublisher;
import com.pikachew.dangUBI.infra.swift.SwiftClient;
import com.pikachew.dangUBI.infra.swift.SwiftClientImpl;

/** 통합 테스트: local이 아닌 프로필에서 운영 구현체가 주입되는지. 접속 정보는 application-test.yaml의 가짜 값이다 */
@SpringBootTest
@ActiveProfiles("test")
class ExternalStorageWiringTest {

	@Autowired
	private SwiftClient swiftClient;

	@Autowired
	private SourceObjectStore sourceObjectStore;

	@Autowired
	private RequeuePublisher requeuePublisher;

	@Test
	void 운영_구현체가_주입된다() {
		assertThat(swiftClient).isInstanceOf(SwiftClientImpl.class);
		assertThat(sourceObjectStore).isInstanceOf(S3SourceObjectStore.class);
		assertThat(requeuePublisher).isInstanceOf(SqsRequeuePublisher.class);
	}

}
