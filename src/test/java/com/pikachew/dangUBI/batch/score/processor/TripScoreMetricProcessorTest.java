package com.pikachew.dangUBI.batch.score.processor;

import static com.pikachew.dangUBI.domain.trip.model.TripSnapshotFixtures.trip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.model.ScoreWeights;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

/** 단위 테스트: trip 하나를 점수 지표로 바꾼다 */
class TripScoreMetricProcessorTest {

	private static final ScoreProperties PROPERTIES = new ScoreProperties("standard", 100, Duration.ofSeconds(4), Duration.ZERO,
			new ScoreWeights(120, 90, 200, 100), Map.of(1, 0.40), Map.of("standard", new ScoreProperties.Basis(125, 1500)));

	private final TripRepository tripRepository = mock(TripRepository.class);
	private final TripMicrotripRepository microtripRepository = mock(TripMicrotripRepository.class);
	private final TripScoreMetricProcessor processor = new TripScoreMetricProcessor(tripRepository, microtripRepository, PROPERTIES);
	private final Driver driver = Driver.builder().vehicleId("0DS614RB2TT925001").build();

	@Test
	void microtrip으로_판정한_이벤트와_dis를_지표로_만든다() {
		Trip trip = completedTrip(trip("0DS614RB2TT925001", 1, "2026-11-24T16:00:00Z", "2026-11-24T16:20:00Z", 12_000));
		given(tripRepository.findById(1L)).willReturn(Optional.of(trip));
		given(microtripRepository.findByTripOrderByCollectedAt(trip)).willReturn(List.of(
				// KST 01:10 급감속(야간 40%), KST 01:10:02 과속 90km/h, em 없는 주기 1건
				mt("2026-11-24T16:10:00Z", 0x10, 60),
				mt("2026-11-24T16:10:02Z", 0x100, 90),
				mt("2026-11-24T16:10:04Z", null, 90)));

		TripScoreMetric metric = processor.process(1L);

		ScoreTally tally = metric.getTally();
		assertThat(metric.getTrip()).isSameAs(trip);
		assertThat(metric.getVehicleId()).isEqualTo("0DS614RB2TT925001");
		assertThat(metric.getEndedAt()).isEqualTo(Instant.parse("2026-11-24T16:20:00Z"));
		assertThat(tally.getDistanceM()).isEqualTo(12_000);
		assertThat(tally.getBrakeCount()).isEqualTo(1);
		assertThat(tally.getWeightedNightBrake()).isCloseTo(0.40, within(1e-9));
		assertThat(tally.getOverspeedKm()).isCloseTo(90 * 2 / 3600.0, within(1e-9));
		assertThat(metric.getUnevaluatedCount()).isEqualTo(1);
	}

	@Test
	void dis가_없으면_거리와_이벤트를_0으로_기록하고_microtrip을_읽지_않는다() {
		Trip trip = completedTrip(new TripSnapshot("0DS614RB2TT925001", "k", null, 1,
				Instant.parse("2026-11-25T00:00:00Z"), Instant.parse("2026-11-25T00:20:00Z"), null, null, null, null));
		given(tripRepository.findById(1L)).willReturn(Optional.of(trip));

		TripScoreMetric metric = processor.process(1L);

		assertThat(metric.getTally()).isEqualTo(ScoreTally.builder().build());
		verify(microtripRepository, never()).findByTripOrderByCollectedAt(trip);
	}

	@Test
	void 종료_시각이_없으면_마지막_수집_시각으로_쌓는다() {
		Trip trip = completedTrip(new TripSnapshot("0DS614RB2TT925001", "k", null, 1, null, null, 1000, null, null, null));
		trip.recordCollectedAt(Instant.parse("2026-11-25T00:19:58Z"));
		given(tripRepository.findById(1L)).willReturn(Optional.of(trip));

		assertThat(processor.process(1L).getEndedAt()).isEqualTo(Instant.parse("2026-11-25T00:19:58Z"));
	}

	@Test
	void trip이_없으면_건너뛴다() {
		given(tripRepository.findById(1L)).willReturn(Optional.empty());

		assertThat(processor.process(1L)).isNull();
	}

	private Trip completedTrip(TripSnapshot snapshot) {
		Trip trip = Trip.builder().driver(driver).vehicleId(driver.getVehicleId()).tid(snapshot.tid()).build();
		trip.complete(snapshot);
		return trip;
	}

	private static TripMicrotrip mt(String collectedAt, Integer eventMask, Integer speed) {
		return TripMicrotrip.builder()
				.idempotencyKey("mt-" + collectedAt)
				.collectedAt(Instant.parse(collectedAt))
				.speed(speed)
				.eventMask(eventMask == null ? null : eventMask.shortValue())
				.build();
	}

}
