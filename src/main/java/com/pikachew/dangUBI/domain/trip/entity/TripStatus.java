package com.pikachew.dangUBI.domain.trip.entity;

/** trip 상태. IN_PROGRESS → COMPLETED 한 방향으로만 바뀐다. */
public enum TripStatus {

	/** microtrip만 들어오고 ty=3(운행 종료)은 아직 오지 않았다. */
	IN_PROGRESS,

	/** ty=3이 들어와 요약값이 채워졌다. */
	COMPLETED

}
