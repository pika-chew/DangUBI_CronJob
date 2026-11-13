package com.pikachew.dangUBI.infra.local.config;

import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_PROPERTY_PREFIX;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * local 프로필의 로컬 저장소 설정.
 *
 * @param root 로컬 저장소 루트. 상대 경로면 실행 위치 기준이다
 */
@ConfigurationProperties(LOCAL_STORAGE_PROPERTY_PREFIX)
public record LocalStorageProperties(Path root) {
}
