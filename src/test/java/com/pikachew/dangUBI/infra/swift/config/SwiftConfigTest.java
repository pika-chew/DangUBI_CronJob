package com.pikachew.dangUBI.infra.swift.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ServiceClientConfiguration;

/** 단위 테스트: Swift s3api용 S3Client 설정. 클라이언트를 만들 때는 네트워크에 접속하지 않는다 */
class SwiftConfigTest {

	private final SwiftConfig config = new SwiftConfig();

	@Test
	void 엔드포인트와_리전을_설정값으로_바꾼_S3Client를_만든다() {
		try (S3Client client = config.swiftS3Client(
				new SwiftProperties("https://swift.test:8080", "ubi-raw", "access", "secret", "RegionOne"))) {
			S3ServiceClientConfiguration configuration = client.serviceClientConfiguration();

			assertThat(configuration.endpointOverride()).contains(URI.create("https://swift.test:8080"));
			assertThat(configuration.region()).isEqualTo(Region.of("RegionOne"));
		}
	}

	@Test
	void 인증_정보가_비어_있으면_시작하지_않는다() {
		assertThatThrownBy(() -> config.swiftS3Client(
				new SwiftProperties("https://swift.test:8080", "ubi-raw", "", "secret", "RegionOne")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SWIFT_ACCESS_KEY");
	}

	@Test
	void 엔드포인트가_없으면_시작하지_않는다() {
		assertThatThrownBy(() -> config.swiftS3Client(
				new SwiftProperties(null, "ubi-raw", "access", "secret", "RegionOne")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SWIFT_ENDPOINT");
	}

}
