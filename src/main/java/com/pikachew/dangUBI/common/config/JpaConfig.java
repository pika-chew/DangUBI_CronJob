package com.pikachew.dangUBI.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseTimeEntity의 createdAt·updatedAt을 자동으로 채운다. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
