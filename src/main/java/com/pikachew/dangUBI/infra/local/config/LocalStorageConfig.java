package com.pikachew.dangUBI.infra.local.config;

import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.LOCAL_PROFILE;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** local 프로필에서만 로컬 저장소 설정을 읽는다 */
@Configuration
@Profile(LOCAL_PROFILE)
@EnableConfigurationProperties(LocalStorageProperties.class)
public class LocalStorageConfig {
}
