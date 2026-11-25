package com.pikachew.dangUBI.batch.score.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * scoreJob: 종료된 trip의 점수 지표를 만들고, 운전자별 구간 감점과 하루 리포트를 남긴다 (docs/score-spec.md).
 */
@Configuration
@EnableConfigurationProperties(ScoreProperties.class)
public class ScoreJobConfig {

}
