package com.pikachew.dangUBI.domain.outbox.entity;

/** outbox 이벤트가 가리키는 대상 종류. aggregate_id는 이 종류의 테이블 id다. */
public enum OutboxAggregateType {

	TRIP,

	/** driver_score_report. 리포트마다 이벤트가 하나라 운전자가 아니라 리포트를 대상으로 한다 */
	DRIVER_SCORE_REPORT

}
