package com.pikachew.dangUBI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import com.pikachew.dangUBI.infra.s3.config.S3Properties;
import com.pikachew.dangUBI.infra.sqs.config.SqsProperties;
import com.pikachew.dangUBI.infra.swift.config.SwiftConfig;
import com.pikachew.dangUBI.infra.swift.config.SwiftProperties;

/** 단위 테스트: application-prod.yaml이 환경 변수를 맞는 설정 키로 옮기는지. DB·외부 저장소에는 접속하지 않는다 */
class ProdProfilePropertiesTest {

	@Test
	void 환경_변수를_DB와_외부_저장소_설정으로_읽는다() throws IOException {
		Binder binder = Binder.get(prodEnvironment(fullEnv()));

		assertThat(binder.bind("spring.datasource.url", String.class).get()).isEqualTo("jdbc:mysql://db:3306/ubi");
		assertThat(binder.bind("spring.datasource.username", String.class).get()).isEqualTo("ubi-user");
		assertThat(binder.bind("spring.datasource.password", String.class).get()).isEqualTo("ubi-pass");
		assertThat(binder.bind("ubi.s3", S3Properties.class).get())
				.isEqualTo(new S3Properties("ubi-source", "ap-northeast-2"));
		assertThat(binder.bind("ubi.sqs", SqsProperties.class).get())
				.isEqualTo(new SqsProperties("https://sqs.test/123/q", "ap-northeast-2"));
		assertThat(binder.bind("ubi.swift", SwiftProperties.class).get())
				.isEqualTo(new SwiftProperties("https://swift.test:8080", "ubi-raw", "ak", "sk", "us-east-1"));
	}

	@Test
	void 스키마를_자동으로_만들지_않는다() throws IOException {
		Binder binder = Binder.get(prodEnvironment(fullEnv()));

		assertThat(binder.bind("spring.batch.jdbc.initialize-schema", String.class).get()).isEqualTo("never");
		assertThat(binder.bind("spring.sql.init.mode", String.class).get()).isEqualTo("never");
	}

	@Test
	void SWIFT_REGION을_주면_기본값_대신_쓴다() throws IOException {
		Map<String, Object> env = fullEnv();
		env.put("SWIFT_REGION", "RegionOne");

		assertThat(Binder.get(prodEnvironment(env)).bind("ubi.swift.region", String.class).get()).isEqualTo("RegionOne");
	}

	@Test
	void 외부_저장소_환경_변수가_없으면_빈_값이_되어_Config_검사에서_실패한다() throws IOException {
		Map<String, Object> env = fullEnv();
		env.remove("SWIFT_ACCESS_KEY");
		SwiftProperties properties = Binder.get(prodEnvironment(env)).bind("ubi.swift", SwiftProperties.class).get();

		assertThat(properties.accessKey()).isEmpty();
		assertThatThrownBy(() -> new SwiftConfig().swiftS3Client(properties))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("SWIFT_ACCESS_KEY");
	}

	private static StandardEnvironment prodEnvironment(Map<String, Object> env) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("env", env));
		new YamlPropertySourceLoader().load("prod", new ClassPathResource("application-prod.yaml"))
				.forEach(environment.getPropertySources()::addLast);
		return environment;
	}

	private static Map<String, Object> fullEnv() {
		Map<String, Object> env = new HashMap<>();
		env.put("SPRING_DATASOURCE_URL", "jdbc:mysql://db:3306/ubi");
		env.put("SPRING_DATASOURCE_USERNAME", "ubi-user");
		env.put("SPRING_DATASOURCE_PASSWORD", "ubi-pass");
		env.put("S3_SOURCE_BUCKET", "ubi-source");
		env.put("AWS_REGION", "ap-northeast-2");
		env.put("SQS_REQUEUE_QUEUE_URL", "https://sqs.test/123/q");
		env.put("SWIFT_ENDPOINT", "https://swift.test:8080");
		env.put("SWIFT_CONTAINER", "ubi-raw");
		env.put("SWIFT_ACCESS_KEY", "ak");
		env.put("SWIFT_SECRET_KEY", "sk");
		return env;
	}

}
