package com.pikachew.dangUBI.domain.common.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 생성·수정 시각을 갖는 엔티티의 공통 부모. trip은 적재 중에 상태가 바뀌어 수정 시각도 둔다.
 * 시각은 UTC 기준이다.
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

}
