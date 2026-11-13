package com.pikachew.dangUBI.infra.sqs.config;

import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/** 자격 증명은 S3 원본과 같이 AWS SDK 기본 체인으로 찾는다 */
@Configuration
@Profile(NOT_LOCAL_PROFILE)
@EnableConfigurationProperties(SqsProperties.class)
public class SqsConfig {

	@Bean
	public SqsClient sqsClient(SqsProperties properties) {
		Assert.hasText(properties.queueUrl(), "ubi.sqs.queue-url(SQS_REQUEUE_QUEUE_URL)이 비어 있습니다");
		Assert.hasText(properties.region(), "ubi.sqs.region(AWS_REGION)이 비어 있습니다");
		return SqsClient.builder()
				.region(Region.of(properties.region()))
				.build();
	}

}
