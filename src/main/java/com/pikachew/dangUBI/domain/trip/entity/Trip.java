package com.pikachew.dangUBI.domain.trip.entity;

import java.time.Instant;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운행 한 번. 키는 (vehicleId, tid)다.
 *
 * microtrip과 ty=3 중 어느 쪽이 먼저 와도 결과가 같도록, 두 메시지가 고치는 컬럼을 나눈다 (docs/ingest-spec.md 6장).
 * - microtrip: {@link #recordCollectedAt}으로 firstCollectedAt·lastCollectedAt만 넓힌다.
 * - ty=3: {@link #complete}로 status와 요약값만 채운다.
 */
@Getter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_trip_vehicle_id_tid", columnNames = {"vehicle_id", "tid"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "driver_id", nullable = false)
	private Driver driver;

	@Column(nullable = false, length = 64)
	private String vehicleId;

	@Column(nullable = false)
	private int tid;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private TripStatus status;

	private Instant startedAt;
	private Instant endedAt;
	private Instant firstCollectedAt;
	private Instant lastCollectedAt;

	private Integer distance;
	private Integer totalDistance;
	private Integer fuelConsumption;
	private Integer startLat;
	private Integer startLon;
	private Integer endLat;
	private Integer endLon;
	private Integer coolantTempAvg;
	private Integer carbonEmission;
	private Integer fuelCutTime;
	private Integer maxSpeed;
	private Integer avgSpeed;
	private Integer idleTime;
	private Integer batteryVoltage;
	private Integer generatorVoltage;
	private Integer warmupTime;
	private Integer highSpeedTime;
	private Integer firmwareVersion;
	private Integer drivingTime;

	@Column(length = 128)
	private String tripIdempotencyKey;
	private Instant tripReceivedAt;

	private Trip(Driver driver, String vehicleId, int tid) {
		this.driver = driver;
		this.vehicleId = vehicleId;
		this.tid = tid;
		this.status = TripStatus.IN_PROGRESS;
	}

	/** 처음 본 trip을 진행 중 상태로 만든다. ty=3이 먼저 오면 바로 {@link #complete}가 이어진다. */
	public static Trip start(Driver driver, String vehicleId, int tid) {
		return new Trip(driver, vehicleId, tid);
	}

	/** microtrip 수집 시각으로 수집 범위를 넓힌다. 순서와 상관없이 최솟값·최댓값만 남는다. */
	public void recordCollectedAt(Instant collectedAt) {
		if (firstCollectedAt == null || collectedAt.isBefore(firstCollectedAt)) {
			firstCollectedAt = collectedAt;
		}
		if (lastCollectedAt == null || collectedAt.isAfter(lastCollectedAt)) {
			lastCollectedAt = collectedAt;
		}
	}

	/** ty=3 요약값을 채우고 종료한다. 같은 메시지를 다시 받아도 같은 값으로 덮어쓸 뿐이다. */
	public void complete(TripRecord record) {
		this.status = TripStatus.COMPLETED;
		this.startedAt = record.startedAt();
		this.endedAt = record.endedAt();
		this.distance = record.distance();
		this.totalDistance = record.totalDistance();
		this.fuelConsumption = record.fuelConsumption();
		this.startLat = record.startLat();
		this.startLon = record.startLon();
		this.endLat = record.endLat();
		this.endLon = record.endLon();
		this.coolantTempAvg = record.coolantTempAvg();
		this.carbonEmission = record.carbonEmission();
		this.fuelCutTime = record.fuelCutTime();
		this.maxSpeed = record.maxSpeed();
		this.avgSpeed = record.avgSpeed();
		this.idleTime = record.idleTime();
		this.batteryVoltage = record.batteryVoltage();
		this.generatorVoltage = record.generatorVoltage();
		this.warmupTime = record.warmupTime();
		this.highSpeedTime = record.highSpeedTime();
		this.firmwareVersion = record.firmwareVersion();
		this.drivingTime = record.drivingTime();
		this.tripIdempotencyKey = record.idempotencyKey();
		this.tripReceivedAt = record.receivedAt();
	}

}
