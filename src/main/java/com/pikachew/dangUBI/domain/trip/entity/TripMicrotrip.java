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

/** 운행 중 주기적으로 받은 상세 데이터 한 건. idempotencyKey로 한 번만 저장한다 */
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

	private Integer lat;
	private Integer lon;
	private Integer locationAccuracy;
	private Integer fuelConsumption;
	private Integer tripDistance;
	private Integer rpm;
	private Integer speed;
	private Short eventMask;
	private Integer engineLoad;
	// 끝 글자 하나만 대문자인 이름은 기본 규칙으로 acc_x가 아니라 accx가 되므로 컬럼 이름을 적는다
	@Column(name = "acc_x")
	private Integer accX;
	@Column(name = "acc_y")
	private Integer accY;
	@Column(name = "gyro_y")
	private Integer gyroY;
	private Integer batteryVoltage;
	private Integer accelPedalPosition;

	@Builder
	private TripMicrotrip(Trip trip, String idempotencyKey, Instant collectedAt, Instant receivedAt,
			Integer lat, Integer lon, Integer locationAccuracy, Integer fuelConsumption, Integer tripDistance,
			Integer rpm, Integer speed, Short eventMask, Integer engineLoad, Integer accX, Integer accY, Integer gyroY,
			Integer batteryVoltage, Integer accelPedalPosition) {
		this.trip = trip;
		this.idempotencyKey = idempotencyKey;
		this.collectedAt = collectedAt;
		this.receivedAt = receivedAt;
		this.lat = lat;
		this.lon = lon;
		this.locationAccuracy = locationAccuracy;
		this.fuelConsumption = fuelConsumption;
		this.tripDistance = tripDistance;
		this.rpm = rpm;
		this.speed = speed;
		this.eventMask = eventMask;
		this.engineLoad = engineLoad;
		this.accX = accX;
		this.accY = accY;
		this.gyroY = gyroY;
		this.batteryVoltage = batteryVoltage;
		this.accelPedalPosition = accelPedalPosition;
	}

}
