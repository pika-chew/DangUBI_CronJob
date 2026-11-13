package com.pikachew.dangUBI.infra.s3.config;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.S3_PROPERTY_PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param bucket Firehose가 수집 파일을 쓰는 버킷
 * @param region 버킷 리전. 예: ap-northeast-2
 */
@ConfigurationProperties(S3_PROPERTY_PREFIX)
public record S3Properties(String bucket, String region) {
}
