package com.pikachew.dangUBI.domain.trip.entity;

import java.time.Instant;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운행 중 주기적으로 받은 상세 데이터 한 건. idempotencyKey로 한 번만 저장한다.
 * 점수 계산에 쓰는 값(수집 시각, 속도, 이벤트 비트)만 저장하고 나머지는 Swift 원본에만 남긴다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripMicrotrip extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "trip_id", nullable = false)
	private Trip trip;

	@Column(nullable = false, unique = true, length = 128)
	private String idempotencyKey;

	@Column(nullable = false)
	private Instant collectedAt;
	private Instant receivedAt;

	private Integer speed;
	private Short eventMask;

	@Builder
	private TripMicrotrip(Trip trip, String idempotencyKey, Instant collectedAt, Instant receivedAt,
			Integer speed, Short eventMask) {
		this.trip = trip;
		this.idempotencyKey = idempotencyKey;
		this.collectedAt = collectedAt;
		this.receivedAt = receivedAt;
		this.speed = speed;
		this.eventMask = eventMask;
	}

}
