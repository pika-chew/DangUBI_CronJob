package com.pikachew.dangUBI.batch.score.writer;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_ZONE;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.model.VehicleScoreReportOutboxPayload;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.score.entity.ScoreEventSummary;
import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.entity.VehicleScoreReport;
import com.pikachew.dangUBI.domain.score.policy.ScorePenaltyPolicy;
import com.pikachew.dangUBI.domain.score.policy.ScoreSegmentPolicy;
import com.pikachew.dangUBI.domain.score.repository.ScoreSegmentRepository;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;
import com.pikachew.dangUBI.domain.score.repository.VehicleScoreReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * scoreReportStep의 Writer: 차량(VIN)마다 구간을 닫고 하루 리포트와 outbox 이벤트를 남긴다 (docs/score-spec.md 5–7장).
 * Repository를 직접 쓰고, chunk 트랜잭션 안에서 실행된다.
 *
 * 1. 구간에 묶이지 않은 지표를 쌓아 구간 거리를 넘을 때마다 구간을 닫는다. 채우지 못한 나머지는 다음 실행까지 남는다.
 * 2. 리포트에 담기지 않은 지표를 "오늘" 카운트로, 최근 반영 범위 안 구간을 반영 감점으로 리포트 하나를 만든다.
 * 3. 리포트를 대상으로 VEHICLE_SCORE_REPORTED를 outbox에 남긴다. 리포트와 같은 chunk 트랜잭션이다.
 * 처리한 지표에는 구간·리포트가 채워지므로 다시 실행해도 같은 구간·리포트를 두 번 만들지 않는다.
 *
 * TODO: 두 실행이 같은 차량을 동시에 처리하면 구간 순번 유니크 충돌로 chunk가 실패한다. 지금은 CronJob 동시 실행 금지로 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoreReportWriter implements ItemWriter<String> {

	private final TripScoreMetricRepository metricRepository;
	private final ScoreSegmentRepository segmentRepository;
	private final VehicleScoreReportRepository reportRepository;
	private final OutboxRepository outboxRepository;
	private final JsonMapper jsonMapper;
	private final ScoreProperties scoreProperties;

	@Override
	public void write(Chunk<? extends String> chunk) {
		LocalDate reportDate = LocalDate.now(SCORE_ZONE);
		ScoreProperties.Basis basis = scoreProperties.currentBasis();
		int closedSegments = 0;
		for (String vehicleId : chunk) {
			closedSegments += closeSegments(vehicleId, basis);
			saveReport(vehicleId, reportDate, basis);
		}
		log.info("score report chunk: 차량 {}대, 닫은 구간 {}개, 리포트 {}건", chunk.size(), closedSegments, chunk.size());
	}

	private int closeSegments(String vehicleId, ScoreProperties.Basis basis) {
		List<TripScoreMetric> unsegmented = metricRepository.findByVehicleIdAndSegmentIsNullOrderByEndedAtAscIdAsc(vehicleId);
		List<List<TripScoreMetric>> groups = ScoreSegmentPolicy.closeSegments(unsegmented, basis.segmentDistanceM());
		int sequenceNo = segmentRepository.findTopByVehicleIdOrderBySequenceNoDesc(vehicleId)
				.map(ScoreSegment::getSequenceNo)
				.orElse(0);
		for (List<TripScoreMetric> group : groups) {
			ScoreTally tally = sum(group);
			ScoreSegment segment = segmentRepository.save(ScoreSegment.builder()
					.vehicleId(vehicleId)
					.sequenceNo(++sequenceNo)
					.tally(tally)
					.penalty(ScorePenaltyPolicy.penalty(tally, scoreProperties.weights()).orElseThrow())
					.build());
			group.forEach(metric -> metric.assignSegment(segment));
		}
		return groups.size();
	}

	private void saveReport(String vehicleId, LocalDate reportDate, ScoreProperties.Basis basis) {
		List<TripScoreMetric> unreported = metricRepository.findByVehicleIdAndReportIsNullOrderByEndedAtAscIdAsc(vehicleId);
		List<ScoreSegment> window = ScoreSegmentPolicy.window(
				segmentRepository.findTop100ByVehicleIdOrderBySequenceNoDesc(vehicleId), basis.windowDistanceM());
		ScoreTally windowTally = window.stream().map(ScoreSegment::getTally).reduce(ScoreTally.builder().build(), ScoreTally::plus);
		OptionalDouble penalty = window.isEmpty() ? OptionalDouble.empty()
				: ScorePenaltyPolicy.penalty(windowTally, scoreProperties.weights());
		Double score = penalty.isPresent() ? ScorePenaltyPolicy.score(penalty.getAsDouble()) : null;
		Double previousScore = reportRepository.findTopByVehicleIdOrderByIdDesc(vehicleId)
				.map(VehicleScoreReport::getScore)
				.orElse(null);

		VehicleScoreReport report = reportRepository.save(VehicleScoreReport.builder()
				.vehicleId(vehicleId)
				.reportDate(reportDate)
				.score(score)
				.penalty(penalty.isPresent() ? penalty.getAsDouble() : null)
				.scoreChange(score == null || previousScore == null ? null : score - previousScore)
				.recentScores(jsonMapper.writeValueAsString(window.stream()
						.map(segment -> ScorePenaltyPolicy.score(segment.getPenalty()))
						.toList()))
				.windowSegmentCount(window.size())
				.today(summary(sum(unreported)))
				.window(summary(windowTally))
				.build());
		unreported.forEach(metric -> metric.assignReport(report));

		outboxRepository.save(Outbox.builder()
				.aggregateType(OutboxAggregateType.VEHICLE_SCORE_REPORT)
				.aggregateId(report.getId())
				.eventType(OutboxEventType.VEHICLE_SCORE_REPORTED)
				.payload(jsonMapper.writeValueAsString(new VehicleScoreReportOutboxPayload(
						report.getId(), vehicleId, reportDate.toString())))
				.build());
	}

	private static ScoreTally sum(List<TripScoreMetric> metrics) {
		return metrics.stream().map(TripScoreMetric::getTally).reduce(ScoreTally.builder().build(), ScoreTally::plus);
	}

	private static ScoreEventSummary summary(ScoreTally tally) {
		return ScoreEventSummary.builder()
				.distanceM(tally.getDistanceM())
				.overspeedKm(tally.getOverspeedKm())
				.accelCount(tally.getAccelCount())
				.brakeCount(tally.getBrakeCount())
				.turnCount(tally.getTurnCount())
				.build();
	}

}
