package com.pikachew.dangUBI.infra.swift.config;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.SWIFT_S3_CLIENT_BEAN;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Swift s3api에 붙는 S3Client를 만든다. Swift는 AWS가 아니므로 엔드포인트를 바꾸고, path-style 주소를 쓰고,
 * Keystone EC2 credentials로 서명한다. local 프로필에서는 만들지 않는다.
 */
@Configuration
@Profile(NOT_LOCAL_PROFILE)
@EnableConfigurationProperties(SwiftProperties.class)
public class SwiftConfig {

	/**
	 * 체크섬은 필요한 요청에만 계산·검증한다(WHEN_REQUIRED). SDK 2.30부터 기본값이 모든 요청에 CRC 체크섬을 붙이는 것으로 바뀌었는데,
	 * S3 호환 저장소가 이 헤더를 모르면 요청이 실패할 수 있다.
	 */
	@Bean(SWIFT_S3_CLIENT_BEAN)
	public S3Client swiftS3Client(SwiftProperties properties) {
		Assert.hasText(properties.endpoint(), "ubi.swift.endpoint(SWIFT_ENDPOINT)가 비어 있습니다");
		Assert.hasText(properties.container(), "ubi.swift.container(SWIFT_CONTAINER)가 비어 있습니다");
		Assert.hasText(properties.accessKey(), "ubi.swift.access-key(SWIFT_ACCESS_KEY)가 비어 있습니다");
		Assert.hasText(properties.secretKey(), "ubi.swift.secret-key(SWIFT_SECRET_KEY)가 비어 있습니다");
		Assert.hasText(properties.region(), "ubi.swift.region(SWIFT_REGION)이 비어 있습니다");
		return S3Client.builder()
				.endpointOverride(URI.create(properties.endpoint()))
				.forcePathStyle(true)
				.region(Region.of(properties.region()))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
				.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
				.responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
				.build();
	}

}
