package com.pikachew.dangUBI.infra.swift.config;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.SWIFT_PROPERTY_PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param endpoint  Swift proxy의 s3api 주소. 예: https://swift.example.com:8080
 * @param container 수집 파일이 있는 컨테이너 (S3의 버킷)
 * @param accessKey Keystone EC2 credentials의 access
 * @param secretKey Keystone EC2 credentials의 secret
 * @param region    서명에 쓰는 리전 이름. s3api의 location 설정과 맞춘다
 */
@ConfigurationProperties(SWIFT_PROPERTY_PREFIX)
public record SwiftProperties(String endpoint, String container, String accessKey, String secretKey, String region) {
}
