package com.pikachew.dangUBI.domain.outbox.entity;

/** outbox 행의 발행 상태. PENDING → PUBLISHED 한 방향으로만 바뀐다. */
public enum OutboxStatus {

	/** 적재와 함께 기록됐고 아직 발행되지 않았다. */
	PENDING,

	/** relay가 발행을 마쳤다. */
	PUBLISHED

}
