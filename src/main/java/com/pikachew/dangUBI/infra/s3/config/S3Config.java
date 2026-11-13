package com.pikachew.dangUBI.infra.s3.config;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.S3_SOURCE_CLIENT_BEAN;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * AWS S3 원본 버킷에 붙는 S3Client를 만든다. local 프로필에서는 만들지 않는다.
 * 자격 증명은 지정하지 않아 SDK 기본 체인(환경 변수 AWS_ACCESS_KEY_ID 등, K8s IRSA, 프로필 파일 순)이 첫 요청 때 찾는다.
 */
@Configuration
@Profile(NOT_LOCAL_PROFILE)
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

	@Bean(S3_SOURCE_CLIENT_BEAN)
	public S3Client sourceS3Client(S3Properties properties) {
		Assert.hasText(properties.bucket(), "ubi.s3.bucket(S3_SOURCE_BUCKET)이 비어 있습니다");
		Assert.hasText(properties.region(), "ubi.s3.region(AWS_REGION)이 비어 있습니다");
		return S3Client.builder()
				.region(Region.of(properties.region()))
				.build();
	}

}
