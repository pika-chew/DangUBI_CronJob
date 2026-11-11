package com.pikachew.dangUBI.domain.trip.service;

import static com.pikachew.dangUBI.common.consts.TripStaticValues.IDEMPOTENCY_KEY_QUERY_BATCH_SIZE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.pikachew.dangUBI.common.annotation.DomainService;
import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import com.pikachew.dangUBI.domain.trip.entity.Driver;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.model.TripIngestResult;
import com.pikachew.dangUBI.domain.trip.repository.DriverRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;

/**
 * trip 적재 (docs/ingest-spec.md 6장 "trip 정제").
 *
 * - trip은 (vehicleId, tid)로 찾고, 없으면 진행 중 상태로 만든다. 운전자도 없으면 만든다.
 * - microtrip은 idempotencyKey로 이미 저장된 것과 입력 안의 중복을 걸러 새 것만 저장하고, trip의 수집 범위를 넓힌다.
 * - ty=3은 trip을 종료하고 요약값을 채운다.
 *
 * TODO: 두 실행이 같은 trip을 동시에 처음 만들면 유니크 충돌로 chunk가 실패한다. 지금은 CronJob 동시 실행 금지로 막는다.
 * TODO: microtrip이 많아 IDENTITY 단건 insert가 느리면 JDBC batch insert로 바꾼다.
 */
@DomainService
@RequiredArgsConstructor
public class TripDomainServiceImpl implements TripDomainService {

	private final DriverRepository driverRepository;
	private final TripRepository tripRepository;
	private final TripMicrotripRepository microtripRepository;

	@Override
	public TripIngestResult ingest(List<TripRecord> trips, List<MicrotripRecord> microtrips) {
		TripFinder tripFinder = new TripFinder();

		Map<String, MicrotripRecord> uniqueMicrotrips = new LinkedHashMap<>();
		microtrips.forEach(record -> uniqueMicrotrips.putIfAbsent(record.idempotencyKey(), record));
		Set<String> existingKeys = findExistingKeys(new ArrayList<>(uniqueMicrotrips.keySet()));

		List<TripMicrotrip> newMicrotrips = new ArrayList<>();
		for (MicrotripRecord record : uniqueMicrotrips.values()) {
			if (existingKeys.contains(record.idempotencyKey())) {
				continue;
			}
			Trip trip = tripFinder.findOrStart(record.vehicleId(), record.tid());
			trip.recordCollectedAt(record.collectedAt());
			newMicrotrips.add(TripMicrotrip.of(trip, record));
		}
		microtripRepository.saveAll(newMicrotrips);

		for (TripRecord record : trips) {
			tripFinder.findOrStart(record.vehicleId(), record.tid()).complete(record);
		}

		return new TripIngestResult(trips.size(), newMicrotrips.size(), microtrips.size() - newMicrotrips.size());
	}

	private Set<String> findExistingKeys(List<String> keys) {
		Set<String> existing = new HashSet<>();
		for (int from = 0; from < keys.size(); from += IDEMPOTENCY_KEY_QUERY_BATCH_SIZE) {
			List<String> batch = keys.subList(from, Math.min(from + IDEMPOTENCY_KEY_QUERY_BATCH_SIZE, keys.size()));
			existing.addAll(microtripRepository.findExistingIdempotencyKeys(batch));
		}
		return existing;
	}

	/** 한 번의 ingest 안에서 같은 trip·운전자를 다시 조회하지 않도록 담아 둔다. */
	private class TripFinder {

		private final Map<String, Driver> drivers = new HashMap<>();
		private final Map<String, Trip> trips = new HashMap<>();

		Trip findOrStart(String vehicleId, int tid) {
			return trips.computeIfAbsent(vehicleId + "#" + tid, key ->
					tripRepository.findByVehicleIdAndTid(vehicleId, tid)
							.orElseGet(() -> tripRepository.save(Trip.start(findOrCreateDriver(vehicleId), vehicleId, tid))));
		}

		private Driver findOrCreateDriver(String vehicleId) {
			return drivers.computeIfAbsent(vehicleId, key ->
					driverRepository.findByVehicleId(vehicleId)
							.orElseGet(() -> driverRepository.save(Driver.ofVehicle(vehicleId))));
		}

	}

}
