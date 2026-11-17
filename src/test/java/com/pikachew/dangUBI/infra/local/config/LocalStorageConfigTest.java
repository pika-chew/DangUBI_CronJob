package com.pikachew.dangUBI.infra.local.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.pikachew.dangUBI.batch.reconcile.port.RequeuePublisher;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.infra.local.LocalRequeuePublisher;
import com.pikachew.dangUBI.infra.local.LocalSourceObjectStore;
import com.pikachew.dangUBI.infra.local.LocalSwiftClient;
import com.pikachew.dangUBI.infra.swift.SwiftClient;

/**
 * 통합 테스트: local 프로필이면 Swift 대신 로컬 구현체가 주입되는지 확인한다.
 * DB는 test 프로필(H2)이 local 프로필(MySQL) 설정을 덮어쓴다.
 */
@SpringBootTest(properties = "ubi.local-storage.root=build/local-storage-test")
@ActiveProfiles({"local", "test"})
class LocalStorageConfigTest {

	@Autowired
	private SwiftClient swiftClient;

	@Autowired
	private SourceObjectStore sourceObjectStore;

	@Autowired
	private RequeuePublisher requeuePublisher;

	@Autowired
	private LocalStorageProperties properties;

	@Test
	void local_프로필이면_SwiftClient로_LocalSwiftClient가_주입된다() {
		assertThat(swiftClient).isInstanceOf(LocalSwiftClient.class);
	}

	@Test
	void local_프로필이면_SourceObjectStore로_LocalSourceObjectStore가_주입된다() {
		assertThat(sourceObjectStore).isInstanceOf(LocalSourceObjectStore.class);
	}

	@Test
	void local_프로필이면_RequeuePublisher로_LocalRequeuePublisher가_주입된다() {
		assertThat(requeuePublisher).isInstanceOf(LocalRequeuePublisher.class);
	}

	@Test
	void 로컬_저장소_루트를_설정에서_읽는다() {
		assertThat(properties.root()).isEqualTo(Path.of("build/local-storage-test"));
	}

}
