package com.pikachew.dangUBI.infra.sqs.config;

import static com.pikachew.dangUBI.common.consts.SqsStaticValues.SQS_PROPERTY_PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param queueUrl 수집 워커가 읽는 큐 URL
 * @param region   큐 리전
 */
@ConfigurationProperties(SQS_PROPERTY_PREFIX)
public record SqsProperties(String queueUrl, String region) {
}
