package com.pikachew.dangUBI.domain.outbox.model;

/** trip 이벤트의 outbox payload. 식별자만 담고, 상세 값은 소비자가 trip 테이블에서 읽는다. */
public record TripOutboxPayload(
		Long tripId,
		Long driverId,
		String vehicleId,
		int tid
) {
}
