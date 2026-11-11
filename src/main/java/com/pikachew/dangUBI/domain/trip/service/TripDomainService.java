package com.pikachew.dangUBI.domain.trip.service;

import java.util.List;

import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import com.pikachew.dangUBI.domain.trip.model.TripIngestResult;

public interface TripDomainService {

	/**
	 * trip(ty=3)과 microtrip(ty=4)을 적재한다. 입력 순서·호출 순서와 상관없이, 같은 데이터를 다시 넣어도 결과가 같다.
	 * 호출한 쪽의 트랜잭션 안에서 실행한다.
	 */
	TripIngestResult ingest(List<TripRecord> trips, List<MicrotripRecord> microtrips);

}
