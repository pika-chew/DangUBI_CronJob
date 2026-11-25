package com.pikachew.dangUBI.domain.outbox.entity;

/** outbox 이벤트 종류. */
public enum OutboxEventType {

	/** trip을 처음 만들었다(IN_PROGRESS). ty=3이 먼저 와도 생성 이벤트가 먼저 남는다. */
	TRIP_STARTED,

	/** trip이 IN_PROGRESS에서 COMPLETED로 바뀌었다. 이미 종료된 trip의 ty=3을 다시 받으면 남기지 않는다. */
	TRIP_COMPLETED,

	/** scoreJob이 운전자 하루 리포트를 남겼다. 값은 driver_score_report에서 읽는다. */
	DRIVER_SCORE_REPORTED

}
