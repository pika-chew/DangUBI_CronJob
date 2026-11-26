package com.pikachew.dangUBI.batch.score.processor;

import java.time.Instant;
import java.util.List;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.model.DrivingEventResult;
import com.pikachew.dangUBI.domain.score.policy.DrivingEventPolicy;
import com.pikachew.dangUBI.domain.score.policy.ScorePenaltyPolicy;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;

/**
 * scoreMetricStep의 Processor: trip 하나의 microtrip으로 이벤트를 판정해 점수 지표를 만든다. 저장은 Writer가 한다.
 *
 * - D는 ty=3의 dis(m)다. dis가 없는 trip은 평가할 수 없어 거리와 이벤트를 모두 0으로 기록한다.
 *   지표를 남겨야 다음 실행에서 다시 잡히지 않는다.
 * - 쌓는 순서는 trip 종료 시각(edt)이다. 없으면 마지막 수집 시각, 그것도 없으면 trip을 마지막으로 고친 시각을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TripScoreMetricProcessor implements ItemProcessor<Long, TripScoreMetric> {

	private final TripRepository tripRepository;
	private final TripMicrotripRepository microtripRepository;
	private final ScoreProperties scoreProperties;

	@Override
	public TripScoreMetric process(Long tripId) {
		Trip trip = tripRepository.findById(tripId).orElse(null);
		if (trip == null) {
			return null;
		}
		TripScoreMetric.TripScoreMetricBuilder metric = TripScoreMetric.builder()
				.trip(trip)
				.vehicleId(trip.getVehicleId())
				.endedAt(endedAt(trip));
		if (trip.getDistance() == null) {
			return metric.tally(ScoreTally.builder().build()).build();
		}
		List<TripMicrotrip> microtrips = microtripRepository.findByTripOrderByCollectedAt(trip);
		DrivingEventResult result = DrivingEventPolicy.evaluate(microtrips, scoreProperties.maneuverGap());
		return metric
				.tally(ScorePenaltyPolicy.tally(result, trip.getDistance(), scoreProperties.nightRates()))
				.unevaluatedCount(result.unevaluatedCount())
				.build();
	}

	private static Instant endedAt(Trip trip) {
		if (trip.getEndedAt() != null) {
			return trip.getEndedAt();
		}
		if (trip.getLastCollectedAt() != null) {
			return trip.getLastCollectedAt();
		}
		return trip.getUpdatedAt();
	}

}
