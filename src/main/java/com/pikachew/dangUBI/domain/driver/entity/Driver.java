package com.pikachew.dangUBI.domain.driver.entity;

import com.pikachew.dangUBI.domain.common.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운전자. 지금은 차량 1대 = 운전자 1명으로 본다.
 *
 * TODO: 운전자와 차량을 매핑하는 규칙이 생기면 vehicleId 대신 매핑 테이블로 찾는다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Driver extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String vehicleId;

	@Builder
	private Driver(String vehicleId) {
		this.vehicleId = vehicleId;
	}

}
