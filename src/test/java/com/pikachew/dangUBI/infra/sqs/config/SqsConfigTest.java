package com.pikachew.dangUBI.infra.sqs.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/** 단위 테스트: SqsClient 설정 */
class SqsConfigTest {

	private final SqsConfig config = new SqsConfig();

	@Test
	void 설정한_리전으로_SqsClient를_만든다() {
		try (SqsClient client = config.sqsClient(new SqsProperties("https://sqs.test/123/q", "ap-northeast-2"))) {
			assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_2);
		}
	}

	@Test
	void 큐_URL이_비어_있으면_시작하지_않는다() {
		assertThatThrownBy(() -> config.sqsClient(new SqsProperties(" ", "ap-northeast-2")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SQS_REQUEUE_QUEUE_URL");
	}

}
