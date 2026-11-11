package com.pikachew.dangUBI.domain.trip.service;

import java.util.List;

import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripIngestResult;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;

public interface TripDomainService {

	/**
	 * trip(ty=3)과 microtrip(ty=4)을 적재한다. 입력 순서·호출 순서와 상관없이, 같은 데이터를 다시 넣어도 결과가 같다.
	 * 트랜잭션은 호출하는 쪽(chunk)이 연다.
	 */
	TripIngestResult ingest(List<TripSnapshot> trips, List<MicrotripSnapshot> microtrips);

}
