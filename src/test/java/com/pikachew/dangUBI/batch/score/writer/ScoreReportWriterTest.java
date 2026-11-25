package com.pikachew.dangUBI.batch.score.writer;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.batch.score.reader.ScoreDriverReader;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.outbox.entity.Outbox;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxAggregateType;
import com.pikachew.dangUBI.domain.outbox.entity.OutboxEventType;
import com.pikachew.dangUBI.domain.outbox.repository.OutboxRepository;
import com.pikachew.dangUBI.domain.score.entity.DriverScoreReport;
import com.pikachew.dangUBI.domain.score.entity.ScoreEventSummary;
import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.ScoreTally;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.repository.DriverScoreReportRepository;
import com.pikachew.dangUBI.domain.score.repository.ScoreSegmentRepository;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * DB 통합 테스트: 운전자마다 구간을 닫고 하루 리포트·outbox 이벤트를 남긴다.
 * test 기준(구간 50km, 반영 범위 600km)과 운영 가중치(과속 120, 급가속 90, 급감속 200, 급회전 100)를 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScoreReportWriterTest {

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private TripScoreMetricRepository metricRepository;

	@Autowired
	private ScoreSegmentRepository segmentRepository;

	@Autowired
	private DriverScoreReportRepository reportRepository;

	@Autowired
	private OutboxRepository outboxRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Autowired
	private ScoreProperties scoreProperties;

	private int tid;

	@Test
	void 구간_거리를_채운_지표로_구간을_닫고_나머지는_남긴다() {
		Driver driver = savedDriver();
		TripScoreMetric first = savedMetric(driver, "2026-11-25T00:00:00Z", tally(30_000, 0, 1));
		TripScoreMetric second = savedMetric(driver, "2026-11-25T01:00:00Z", tally(30_000, 2, 0));
		TripScoreMetric rest = savedMetric(driver, "2026-11-25T02:00:00Z", tally(10_000, 0, 0));

		writer().write(new Chunk<>(List.of(driver.getId())));

		List<ScoreSegment> segments = segmentRepository.findTop100ByDriverOrderBySequenceNoDesc(driver);
		assertThat(segments).hasSize(1);
		assertThat(segments.get(0).getSequenceNo()).isEqualTo(1);
		assertThat(segments.get(0).getTally().getDistanceM()).isEqualTo(60_000);
		// (급가속 2회 × 90 + 급감속 1회 × 200) / 60km
		assertThat(segments.get(0).getPenalty()).isCloseTo(380 / 60.0, within(1e-9));
		assertThat(first.getSegment()).isSameAs(segments.get(0));
		assertThat(second.getSegment()).isSameAs(segments.get(0));
		assertThat(rest.getSegment()).isNull();
	}

	@Test
	void 오늘_카운트와_반영_감점으로_리포트를_남기고_outbox에_이벤트를_기록한다() {
		Driver driver = savedDriver();
		savedMetric(driver, "2026-11-25T00:00:00Z", tally(30_000, 0, 1));
		savedMetric(driver, "2026-11-25T01:00:00Z", tally(30_000, 2, 0));
		savedMetric(driver, "2026-11-25T02:00:00Z", tally(10_000, 0, 0));

		writer().write(new Chunk<>(List.of(driver.getId())));

		DriverScoreReport report = reportRepository.findTopByDriverOrderByIdDesc(driver).orElseThrow();
		assertThat(report.getVehicleId()).isEqualTo("0DS614RB2TT925001");
		assertThat(report.getReportDate()).isEqualTo(LocalDate.now(SCORE_ZONE));
		assertThat(report.getPenalty()).isCloseTo(380 / 60.0, within(1e-9));
		assertThat(report.getScore()).isCloseTo(100 - 380 / 60.0, within(1e-9));
		assertThat(report.getScoreChange()).isNull();
		assertThat(report.getWindowSegmentCount()).isEqualTo(1);
		assertThat(report.getToday()).isEqualTo(ScoreEventSummary.builder().distanceM(70_000).accelCount(2).brakeCount(1).build());
		assertThat(report.getWindow()).isEqualTo(ScoreEventSummary.builder().distanceM(60_000).accelCount(2).brakeCount(1).build());
		assertThat(jsonMapper.readValue(report.getRecentScores(), double[].class))
				.containsExactly(new double[] {100 - 380 / 60.0}, within(1e-9));
		assertThat(metricRepository.findByDriverAndReportIsNullOrderByEndedAtAscIdAsc(driver)).isEmpty();

		List<Outbox> outboxes = outboxRepository.findAllByAggregateTypeAndAggregateIdOrderById(
				OutboxAggregateType.DRIVER_SCORE_REPORT, report.getId());
		assertThat(outboxes).extracting(Outbox::getEventType).containsExactly(OutboxEventType.DRIVER_SCORE_REPORTED);
		assertThat(outboxes.get(0).getPayload()).contains("\"reportId\":" + report.getId(), "\"vehicleId\":\"0DS614RB2TT925001\"");
	}

	@Test
	void 닫힌_구간이_없으면_점수는_미산출이다() {
		Driver driver = savedDriver();
		savedMetric(driver, "2026-11-25T00:00:00Z", tally(10_000, 1, 0));

		writer().write(new Chunk<>(List.of(driver.getId())));

		DriverScoreReport report = reportRepository.findTopByDriverOrderByIdDesc(driver).orElseThrow();
		assertThat(report.getScore()).isNull();
		assertThat(report.getPenalty()).isNull();
		assertThat(report.getRecentScores()).isEqualTo("[]");
		assertThat(report.getToday().getAccelCount()).isEqualTo(1);
	}

	@Test
	void 다음_리포트는_새_구간을_반영_범위에_더하고_직전_점수와의_차이를_남긴다() {
		Driver driver = savedDriver();
		savedMetric(driver, "2026-11-25T00:00:00Z", tally(30_000, 0, 1));
		savedMetric(driver, "2026-11-25T01:00:00Z", tally(30_000, 2, 0));
		savedMetric(driver, "2026-11-25T02:00:00Z", tally(10_000, 0, 0));
		writer().write(new Chunk<>(List.of(driver.getId())));
		double firstScore = reportRepository.findTopByDriverOrderByIdDesc(driver).orElseThrow().getScore();

		// 남아 있던 10km + 새 45km = 55km로 두 번째 구간이 닫힌다
		savedMetric(driver, "2026-11-26T00:00:00Z", tally(45_000, 0, 0));
		writer().write(new Chunk<>(List.of(driver.getId())));

		DriverScoreReport second = reportRepository.findTopByDriverOrderByIdDesc(driver).orElseThrow();
		assertThat(segmentRepository.findTop100ByDriverOrderBySequenceNoDesc(driver))
				.extracting(ScoreSegment::getSequenceNo).containsExactly(2, 1);
		assertThat(second.getWindowSegmentCount()).isEqualTo(2);
		assertThat(second.getPenalty()).isCloseTo(380 / 115.0, within(1e-9));
		assertThat(second.getScoreChange()).isCloseTo(second.getScore() - firstScore, within(1e-9));
		assertThat(second.getToday().getDistanceM()).isEqualTo(45_000);
	}

	@Test
	void Reader는_리포트에_담지_않은_지표가_있는_운전자만_id_순서로_넘긴다() {
		Driver done = savedDriver();
		savedMetric(done, "2026-11-25T00:00:00Z", tally(10_000, 0, 0));
		writer().write(new Chunk<>(List.of(done.getId())));
		Driver pendingA = driverRepository.save(Driver.builder().vehicleId("VIN-A").build());
		Driver pendingB = driverRepository.save(Driver.builder().vehicleId("VIN-B").build());
		savedMetric(pendingB, "2026-11-25T00:00:00Z", tally(10_000, 0, 0));
		savedMetric(pendingA, "2026-11-25T00:00:00Z", tally(10_000, 0, 0));
		savedMetric(pendingA, "2026-11-25T01:00:00Z", tally(10_000, 0, 0));
		metricRepository.flush();

		ScoreDriverReader reader = new ScoreDriverReader(metricRepository, 1);
		List<Long> ids = new ArrayList<>();
		for (Long id = reader.read(); id != null; id = reader.read()) {
			ids.add(id);
		}

		assertThat(ids).containsExactly(pendingA.getId(), pendingB.getId());
	}

	private ScoreReportWriter writer() {
		return new ScoreReportWriter(driverRepository, metricRepository, segmentRepository, reportRepository,
				outboxRepository, jsonMapper, scoreProperties, "test");
	}

	private Driver savedDriver() {
		return driverRepository.save(Driver.builder().vehicleId("0DS614RB2TT925001").build());
	}

	private TripScoreMetric savedMetric(Driver driver, String endedAt, ScoreTally tally) {
		Trip trip = tripRepository.save(Trip.builder().driver(driver).vehicleId(driver.getVehicleId()).tid(++tid).build());
		return metricRepository.save(TripScoreMetric.builder()
				.trip(trip)
				.driver(driver)
				.endedAt(Instant.parse(endedAt))
				.tally(tally)
				.build());
	}

	private static ScoreTally tally(long distanceM, int accelCount, int brakeCount) {
		return ScoreTally.builder().distanceM(distanceM).accelCount(accelCount).brakeCount(brakeCount).build();
	}

}
