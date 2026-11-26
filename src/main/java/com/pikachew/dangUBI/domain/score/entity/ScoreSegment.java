package com.pikachew.dangUBI.domain.score.entity;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차량별로 trip 지표를 쌓아 구간 거리를 넘었을 때 닫은 구간 하나 (docs/score-spec.md 5장).
 * 닫힌 구간만 저장한다. 닫힌 뒤에는 바뀌지 않는다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_score_segment_vehicle_id_sequence_no",
		columnNames = {"vehicle_id", "sequence_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoreSegment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 차량 식별자 (VIN) */
	@Column(nullable = false, length = 64)
	private String vehicleId;

	/** 차량별 1부터 매기는 구간 순번 */
	@Column(nullable = false)
	private int sequenceNo;

	@Embedded
	private ScoreTally tally;

	/** 이 구간만으로 계산한 감점 */
	@Column(nullable = false)
	private double penalty;

	@Builder
	private ScoreSegment(String vehicleId, int sequenceNo, ScoreTally tally, double penalty) {
		this.vehicleId = vehicleId;
		this.sequenceNo = sequenceNo;
		this.tally = tally;
		this.penalty = penalty;
	}

}
