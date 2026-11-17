package com.pikachew.dangUBI.infra.s3.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** 단위 테스트: S3 원본용 S3Client 설정. 클라이언트를 만들 때는 네트워크·자격 증명을 쓰지 않는다 */
class S3ConfigTest {

	private final S3Config config = new S3Config();

	@Test
	void 설정한_리전으로_S3Client를_만든다() {
		try (S3Client client = config.sourceS3Client(new S3Properties("ubi-source", "ap-northeast-2"))) {
			assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.AP_NORTHEAST_2);
			assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
		}
	}

	@Test
	void 버킷이_비어_있으면_시작하지_않는다() {
		assertThatThrownBy(() -> config.sourceS3Client(new S3Properties("", "ap-northeast-2")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("S3_SOURCE_BUCKET");
	}

}
