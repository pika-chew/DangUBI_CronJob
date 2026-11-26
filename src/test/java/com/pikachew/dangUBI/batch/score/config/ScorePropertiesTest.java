package com.pikachew.dangUBI.batch.score.config;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_PROPERTY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.domain.score.model.ScoreWeights;

/** 단위 테스트: application.yaml의 점수 설정을 읽고 기준을 고른다 */
class ScorePropertiesTest {

	@Test
	void application_yaml의_점수_설정을_읽는다() throws IOException {
		ScoreProperties properties = load();

		assertThat(properties.maneuverGap()).isEqualTo(Duration.ofSeconds(4));
		assertThat(properties.settleDelay()).isEqualTo(Duration.ofHours(24));
		assertThat(properties.weights()).isEqualTo(new ScoreWeights(120, 90, 200, 100));
		assertThat(properties.nightRates()).containsOnlyKeys(23, 0, 1, 2, 3).containsEntry(1, 0.40);
	}

	@Test
	void 기본은_운영_기준_125km_1500km다() throws IOException {
		ScoreProperties properties = load();

		assertThat(properties.basis()).isEqualTo("standard");
		assertThat(properties.currentBasis()).isEqualTo(new ScoreProperties.Basis(125, 1500));
		assertThat(properties.currentBasis().segmentDistanceM()).isEqualTo(125_000);
	}

	@Test
	void test_기준은_50km_600km다() throws IOException {
		assertThat(withBasis(load(), "test").currentBasis()).isEqualTo(new ScoreProperties.Basis(50, 600));
	}

	@Test
	void 기준_이름이_없으면_운영_기준을_쓴다() throws IOException {
		assertThat(withBasis(load(), null).basis()).isEqualTo("standard");
	}

	@Test
	void 없는_기준_이름이면_설정을_만들_수_없다() throws IOException {
		ScoreProperties properties = load();

		assertThatThrownBy(() -> withBasis(properties, "fast"))
				.isInstanceOfSatisfying(GeneralException.class,
						e -> assertThat(e.getCode()).isEqualTo(ErrorStatus.SCORE_UNKNOWN_BASIS));
	}

	private static ScoreProperties withBasis(ScoreProperties properties, String basis) {
		return new ScoreProperties(basis, properties.chunkSize(), properties.maneuverGap(), properties.settleDelay(), properties.weights(),
				properties.nightRates(), properties.bases());
	}

	private static ScoreProperties load() throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
				.forEach(environment.getPropertySources()::addLast);
		return Binder.get(environment).bind(SCORE_PROPERTY_PREFIX, ScoreProperties.class).get();
	}

}
