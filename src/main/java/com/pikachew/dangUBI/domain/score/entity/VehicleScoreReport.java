package com.pikachew.dangUBI.domain.score.entity;

import java.time.LocalDate;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량 하루 리포트 (docs/score-spec.md 7장). scoreJob이 새로 지표를 만든 차량마다 실행당 하나 남긴다.
 * 점수는 차량(VIN) 단위다. 한 사람이 여러 차량을 타는 경우의 합산은 받는 쪽(query server)이 한다.
 * 받는 쪽은 outbox 이벤트(VEHICLE_SCORE_REPORTED)를 받고 이 테이블에서 필요한 항목만 읽는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleScoreReport extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 차량 식별자 (VIN) */
	@Column(nullable = false, length = 64)
	private String vehicleId;

	/** 리포트를 만든 날짜 (KST) */
	@Column(nullable = false)
	private LocalDate reportDate;

	/** 점수 = 100 − 반영 감점. 닫힌 구간이 아직 없으면 null(미산출)이다 */
	private Double score;

	/** 반영 범위 안 구간의 거리 가중 평균 감점. 닫힌 구간이 없으면 null이다 */
	private Double penalty;

	/** 직전 리포트 대비 점수 변화. 직전 리포트가 없거나 어느 한쪽이 미산출이면 null이다 */
	private Double scoreChange;

	/** 반영 범위 안 구간별 점수를 오래된 순서로 담은 JSON 배열 */
	@Column(nullable = false, length = 1024)
	private String recentScores;

	/** 반영 범위에 들어간 구간 수 */
	@Column(nullable = false)
	private int windowSegmentCount;

	/** 이번 리포트에 담긴 trip(오늘 처리한 trip)의 카운트 */
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "distanceM", column = @Column(name = "today_distance_m", nullable = false)),
			@AttributeOverride(name = "overspeedKm", column = @Column(name = "today_overspeed_km", nullable = false)),
			@AttributeOverride(name = "accelCount", column = @Column(name = "today_accel_count", nullable = false)),
			@AttributeOverride(name = "brakeCount", column = @Column(name = "today_brake_count", nullable = false)),
			@AttributeOverride(name = "turnCount", column = @Column(name = "today_turn_count", nullable = false))
	})
	private ScoreEventSummary today;

	/** 반영 범위 안 구간의 카운트 합 */
	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "distanceM", column = @Column(name = "window_distance_m", nullable = false)),
			@AttributeOverride(name = "overspeedKm", column = @Column(name = "window_overspeed_km", nullable = false)),
			@AttributeOverride(name = "accelCount", column = @Column(name = "window_accel_count", nullable = false)),
			@AttributeOverride(name = "brakeCount", column = @Column(name = "window_brake_count", nullable = false)),
			@AttributeOverride(name = "turnCount", column = @Column(name = "window_turn_count", nullable = false))
	})
	private ScoreEventSummary window;

	@Builder
	private VehicleScoreReport(String vehicleId, LocalDate reportDate, Double score, Double penalty,
			Double scoreChange, String recentScores, int windowSegmentCount, ScoreEventSummary today, ScoreEventSummary window) {
		this.vehicleId = vehicleId;
		this.reportDate = reportDate;
		this.score = score;
		this.penalty = penalty;
		this.scoreChange = scoreChange;
		this.recentScores = recentScores;
		this.windowSegmentCount = windowSegmentCount;
		this.today = today;
		this.window = window;
	}

}
