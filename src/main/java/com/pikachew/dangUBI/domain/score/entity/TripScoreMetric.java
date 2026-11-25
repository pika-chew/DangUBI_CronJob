package com.pikachew.dangUBI.domain.score.entity;

import java.time.Instant;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.trip.entity.Trip;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 종료된 trip 하나의 점수 지표. trip마다 한 번만 만든다 (docs/score-spec.md 4장).
 *
 * 만든 뒤에는 구간에 묶이고({@link #assignSegment}), 하루 리포트에 담긴다({@link #assignReport}).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScoreMetric extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false, unique = true)
	private Trip trip;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	/** 구간에 쌓는 순서의 기준. trip의 종료 시각(edt)이다 */
	@Column(nullable = false)
	private Instant endedAt;

	@Embedded
	private ScoreTally tally;

	/** em이 없어 평가하지 못한 microtrip 수 */
	@Column(nullable = false)
	private int unevaluatedCount;

	/** 이 지표가 묶인 구간. 아직 구간 거리를 채우지 못했으면 null이다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "segment_id")
	private ScoreSegment segment;

	/** 이 지표를 "오늘" 카운트로 담은 리포트. 아직 리포트에 담기지 않았으면 null이다 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "report_id")
	private DriverScoreReport report;

	@Builder
	private TripScoreMetric(Trip trip, Driver driver, Instant endedAt, ScoreTally tally, int unevaluatedCount) {
		this.trip = trip;
		this.driver = driver;
		this.endedAt = endedAt;
		this.tally = tally;
		this.unevaluatedCount = unevaluatedCount;
	}

	public void assignSegment(ScoreSegment segment) {
		this.segment = segment;
	}

	public void assignReport(DriverScoreReport report) {
		this.report = report;
	}

}
