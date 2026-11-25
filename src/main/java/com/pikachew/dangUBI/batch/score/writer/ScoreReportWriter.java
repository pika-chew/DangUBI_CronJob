package com.pikachew.dangUBI.batch.score.writer;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_BASIS_EXPRESSION;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_ZONE;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalDouble;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.model.DriverScoreReportOutboxPayload;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.score.entity.DriverScoreReport;
import com.pikachew.dangUBI.domain.score.entity.ScoreEventSummary;
import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.policy.ScorePenaltyPolicy;
import com.pikachew.dangUBI.domain.score.policy.ScoreSegmentPolicy;
import com.pikachew.dangUBI.domain.score.repository.DriverScoreReportRepository;
import com.pikachew.dangUBI.domain.score.repository.ScoreSegmentRepository;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * scoreReportStep의 Writer: 운전자마다 구간을 닫고 하루 리포트와 outbox 이벤트를 남긴다 (docs/score-spec.md 5–7장).
 * Repository를 직접 쓰고, chunk 트랜잭션 안에서 실행된다.
 *
 * 1. 구간에 묶이지 않은 지표를 쌓아 구간 거리를 넘을 때마다 구간을 닫는다. 채우지 못한 나머지는 다음 실행까지 남는다.
 * 2. 리포트에 담기지 않은 지표를 "오늘" 카운트로, 최근 반영 범위 안 구간을 반영 감점으로 리포트 하나를 만든다.
 * 3. 리포트를 대상으로 DRIVER_SCORE_REPORTED를 outbox에 남긴다. 리포트와 같은 chunk 트랜잭션이다.
 * 처리한 지표에는 구간·리포트가 채워지므로 다시 실행해도 같은 구간·리포트를 두 번 만들지 않는다.
 *
 * TODO: 두 실행이 같은 운전자를 동시에 처리하면 구간 순번 유니크 충돌로 chunk가 실패한다. 지금은 CronJob 동시 실행 금지로 막는다.
 */
@Slf4j
@Component
@StepScope
public class ScoreReportWriter implements ItemWriter<Long> {

	private final DriverRepository driverRepository;
	private final TripScoreMetricRepository metricRepository;
	private final ScoreSegmentRepository segmentRepository;
	private final DriverScoreReportRepository reportRepository;
	private final OutboxRepository outboxRepository;
	private final JsonMapper jsonMapper;
	private final ScoreProperties scoreProperties;
	private final ScoreProperties.Basis basis;

	public ScoreReportWriter(DriverRepository driverRepository, TripScoreMetricRepository metricRepository,
			ScoreSegmentRepository segmentRepository, DriverScoreReportRepository reportRepository,
			OutboxRepository outboxRepository, JsonMapper jsonMapper, ScoreProperties scoreProperties,
			@Value(SCORE_BASIS_EXPRESSION) String basisName) {
		this.driverRepository = driverRepository;
		this.metricRepository = metricRepository;
		this.segmentRepository = segmentRepository;
		this.reportRepository = reportRepository;
		this.outboxRepository = outboxRepository;
		this.jsonMapper = jsonMapper;
		this.scoreProperties = scoreProperties;
		this.basis = scoreProperties.basis(basisName);
	}

	@Override
	public void write(Chunk<? extends Long> chunk) {
		LocalDate reportDate = LocalDate.now(SCORE_ZONE);
		int closedSegments = 0;
		int reports = 0;
		for (Long driverId : chunk) {
			Driver driver = driverRepository.findById(driverId).orElse(null);
			if (driver == null) {
				continue;
			}
			closedSegments += closeSegments(driver);
			saveReport(driver, reportDate);
			reports++;
		}
		log.info("score report chunk: 운전자 {}명, 닫은 구간 {}개, 리포트 {}건", chunk.size(), closedSegments, reports);
	}

	private int closeSegments(Driver driver) {
		List<TripScoreMetric> unsegmented = metricRepository.findByDriverAndSegmentIsNullOrderByEndedAtAscIdAsc(driver);
		List<List<TripScoreMetric>> groups = ScoreSegmentPolicy.closeSegments(unsegmented, basis.segmentDistanceM());
		int sequenceNo = segmentRepository.findTopByDriverOrderBySequenceNoDesc(driver)
				.map(ScoreSegment::getSequenceNo)
				.orElse(0);
		for (List<TripScoreMetric> group : groups) {
			ScoreTally tally = sum(group);
			ScoreSegment segment = segmentRepository.save(ScoreSegment.builder()
					.driver(driver)
					.sequenceNo(++sequenceNo)
					.tally(tally)
					.penalty(ScorePenaltyPolicy.penalty(tally, scoreProperties.weights()).orElseThrow())
					.build());
			group.forEach(metric -> metric.assignSegment(segment));
		}
		return groups.size();
	}

	private void saveReport(Driver driver, LocalDate reportDate) {
		List<TripScoreMetric> unreported = metricRepository.findByDriverAndReportIsNullOrderByEndedAtAscIdAsc(driver);
		List<ScoreSegment> window = ScoreSegmentPolicy.window(
				segmentRepository.findTop100ByDriverOrderBySequenceNoDesc(driver), basis.windowDistanceM());
		ScoreTally windowTally = window.stream().map(ScoreSegment::getTally).reduce(ScoreTally.builder().build(), ScoreTally::plus);
		OptionalDouble penalty = window.isEmpty() ? OptionalDouble.empty()
				: ScorePenaltyPolicy.penalty(windowTally, scoreProperties.weights());
		Double score = penalty.isPresent() ? ScorePenaltyPolicy.score(penalty.getAsDouble()) : null;
		Double previousScore = reportRepository.findTopByDriverOrderByIdDesc(driver)
				.map(DriverScoreReport::getScore)
				.orElse(null);

		DriverScoreReport report = reportRepository.save(DriverScoreReport.builder()
				.driver(driver)
				.vehicleId(driver.getVehicleId())
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
				.aggregateType(OutboxAggregateType.DRIVER_SCORE_REPORT)
				.aggregateId(report.getId())
				.eventType(OutboxEventType.DRIVER_SCORE_REPORTED)
				.payload(jsonMapper.writeValueAsString(new DriverScoreReportOutboxPayload(
						report.getId(), driver.getId(), driver.getVehicleId(), reportDate.toString())))
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
