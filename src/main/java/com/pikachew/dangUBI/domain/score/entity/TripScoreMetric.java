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

	@Builder
	private TripScoreMetric(Trip trip, Driver driver, Instant endedAt, ScoreTally tally, int unevaluatedCount) {
		this.trip = trip;
		this.driver = driver;
		this.endedAt = endedAt;
		this.tally = tally;
		this.unevaluatedCount = unevaluatedCount;
	}

}
