package com.pikachew.dangUBI.batch.ingest.writer;

import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_IDEMPOTENCY_KEY_QUERY_BATCH_SIZE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;
import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;
import com.pikachew.dangUBI.domain.trip.repository.TripMicrotripRepository;
import com.pikachew.dangUBI.domain.trip.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ingestJob의 Writer: 파일 여러 개(chunk)의 trip·microtrip을 MySQL에 적재한다 (docs/ingest-spec.md 6장 "trip 정제").
 * Repository를 직접 쓰고, chunk 트랜잭션 안에서 실행된다.
 *
 * 1. microtrip: 같은 chunk 안의 중복과 이미 저장된 idempotencyKey를 걸러 새 것만 남긴다.
 * 2. trip: 필요한 (vehicleId, tid)를 조회하고, 없는 운전자와 trip을 만든다.
 * 3. 새 microtrip을 저장하고 trip의 수집 범위를 넓힌다.
 * 4. ty=3으로 trip을 종료한다.
 * 두 메시지가 trip의 서로 다른 컬럼만 고치므로 순서와 상관없이, 다시 넣어도 결과가 같다.
 *
 * TODO: 두 실행이 같은 trip을 동시에 처음 만들면 유니크 충돌로 chunk가 실패한다. 지금은 CronJob 동시 실행 금지로 막는다.
 * TODO: microtrip이 많아 IDENTITY 단건 insert가 느리면 JDBC batch insert로 바꾼다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestFileWriter implements ItemWriter<ParsedTelemetryFile> {

	private final DriverRepository driverRepository;
	private final TripRepository tripRepository;
	private final TripMicrotripRepository microtripRepository;

	@Override
	public void write(Chunk<? extends ParsedTelemetryFile> chunk) {
		List<TripSnapshot> tripSnapshots = new ArrayList<>();
		List<MicrotripSnapshot> microtripSnapshots = new ArrayList<>();
		int skippedLines = 0;
		for (ParsedTelemetryFile file : chunk) {
			tripSnapshots.addAll(file.trips());
			microtripSnapshots.addAll(file.microtrips());
			skippedLines += file.skippedLineCount();
		}

		List<MicrotripSnapshot> newMicrotrips = excludeSaved(distinctByKey(microtripSnapshots));

		Set<TripKey> tripKeys = new LinkedHashSet<>();
		newMicrotrips.forEach(snapshot -> tripKeys.add(new TripKey(snapshot.vehicleId(), snapshot.tid())));
		tripSnapshots.forEach(snapshot -> tripKeys.add(new TripKey(snapshot.vehicleId(), snapshot.tid())));

		Map<TripKey, Trip> trips = queryTrips(tripKeys);
		Set<TripKey> missingTrips = new LinkedHashSet<>(tripKeys);
		missingTrips.removeAll(trips.keySet());

		Set<String> vehicleIds = missingTrips.stream().map(TripKey::vehicleId).collect(Collectors.toCollection(LinkedHashSet::new));
		Map<String, Driver> drivers = queryDrivers(vehicleIds);
		Set<String> missingDrivers = new LinkedHashSet<>(vehicleIds);
		missingDrivers.removeAll(drivers.keySet());
		drivers.putAll(createDrivers(missingDrivers));
		trips.putAll(createTrips(missingTrips, drivers));

		saveMicrotrips(newMicrotrips, trips);
		recordCollectedAt(newMicrotrips, trips);
		completeTrips(tripSnapshots, trips);

		log.info("ingest chunk: 파일 {}개, microtrip 저장 {}건·중복 {}건, trip 종료 {}건, 새 trip {}건, 건너뛴 줄 {}줄",
				chunk.size(), newMicrotrips.size(), microtripSnapshots.size() - newMicrotrips.size(),
				tripSnapshots.size(), missingTrips.size(), skippedLines);
	}

	/** 같은 idempotencyKey가 여러 번 있으면 처음 것만 남긴다 */
	private static List<MicrotripSnapshot> distinctByKey(List<MicrotripSnapshot> snapshots) {
		Map<String, MicrotripSnapshot> unique = new LinkedHashMap<>();
		snapshots.forEach(snapshot -> unique.putIfAbsent(snapshot.idempotencyKey(), snapshot));
		return new ArrayList<>(unique.values());
	}

	/** 이미 저장된 idempotencyKey를 뺀다 */
	private List<MicrotripSnapshot> excludeSaved(List<MicrotripSnapshot> snapshots) {
		Set<String> savedKeys = querySavedKeys(snapshots.stream().map(MicrotripSnapshot::idempotencyKey).toList());
		return snapshots.stream()
				.filter(snapshot -> !savedKeys.contains(snapshot.idempotencyKey()))
				.toList();
	}

	private Set<String> querySavedKeys(List<String> keys) {
		Set<String> saved = new HashSet<>();
		for (int from = 0; from < keys.size(); from += INGEST_IDEMPOTENCY_KEY_QUERY_BATCH_SIZE) {
			List<String> batch = keys.subList(from, Math.min(from + INGEST_IDEMPOTENCY_KEY_QUERY_BATCH_SIZE, keys.size()));
			saved.addAll(microtripRepository.findExistingIdempotencyKeys(batch));
		}
		return saved;
	}

	private Map<TripKey, Trip> queryTrips(Set<TripKey> keys) {
		if (keys.isEmpty()) {
			return new HashMap<>();
		}
		Set<String> vehicleIds = keys.stream().map(TripKey::vehicleId).collect(Collectors.toSet());
		Set<Integer> tids = keys.stream().map(TripKey::tid).collect(Collectors.toSet());
		return tripRepository.findAllByVehicleIdInAndTidIn(vehicleIds, tids).stream()
				.filter(trip -> keys.contains(new TripKey(trip.getVehicleId(), trip.getTid())))
				.collect(Collectors.toMap(trip -> new TripKey(trip.getVehicleId(), trip.getTid()), Function.identity(),
						(a, b) -> a, HashMap::new));
	}

	private Map<String, Driver> queryDrivers(Collection<String> vehicleIds) {
		if (vehicleIds.isEmpty()) {
			return new HashMap<>();
		}
		return driverRepository.findAllByVehicleIdIn(vehicleIds).stream()
				.collect(Collectors.toMap(Driver::getVehicleId, Function.identity(), (a, b) -> a, HashMap::new));
	}

	private Map<String, Driver> createDrivers(Collection<String> vehicleIds) {
		List<Driver> created = vehicleIds.stream()
				.map(vehicleId -> Driver.builder()
						.vehicleId(vehicleId)
						.build())
				.toList();
		return driverRepository.saveAll(created).stream()
				.collect(Collectors.toMap(Driver::getVehicleId, Function.identity()));
	}

	/** 처음 본 trip은 진행 중 상태로 만든다. ty=3이 같이 왔으면 {@link #completeTrips}가 이어서 종료한다 */
	private Map<TripKey, Trip> createTrips(Collection<TripKey> keys, Map<String, Driver> drivers) {
		List<Trip> created = keys.stream()
				.map(key -> Trip.builder()
						.driver(drivers.get(key.vehicleId()))
						.vehicleId(key.vehicleId())
						.tid(key.tid())
						.build())
				.toList();
		return tripRepository.saveAll(created).stream()
				.collect(Collectors.toMap(trip -> new TripKey(trip.getVehicleId(), trip.getTid()), Function.identity()));
	}

	private void saveMicrotrips(List<MicrotripSnapshot> snapshots, Map<TripKey, Trip> trips) {
		List<TripMicrotrip> microtrips = snapshots.stream()
				.map(snapshot -> toMicrotrip(trips.get(new TripKey(snapshot.vehicleId(), snapshot.tid())), snapshot))
				.toList();
		microtripRepository.saveAll(microtrips);
	}

	private static void recordCollectedAt(List<MicrotripSnapshot> snapshots, Map<TripKey, Trip> trips) {
		snapshots.forEach(snapshot ->
				trips.get(new TripKey(snapshot.vehicleId(), snapshot.tid())).recordCollectedAt(snapshot.collectedAt()));
	}

	private static void completeTrips(List<TripSnapshot> snapshots, Map<TripKey, Trip> trips) {
		snapshots.forEach(snapshot -> trips.get(new TripKey(snapshot.vehicleId(), snapshot.tid())).complete(snapshot));
	}

	private static TripMicrotrip toMicrotrip(Trip trip, MicrotripSnapshot snapshot) {
		return TripMicrotrip.builder()
				.trip(trip)
				.idempotencyKey(snapshot.idempotencyKey())
				.collectedAt(snapshot.collectedAt())
				.receivedAt(snapshot.receivedAt())
				.lat(snapshot.lat())
				.lon(snapshot.lon())
				.locationAccuracy(snapshot.locationAccuracy())
				.fuelConsumption(snapshot.fuelConsumption())
				.tripDistance(snapshot.tripDistance())
				.rpm(snapshot.rpm())
				.speed(snapshot.speed())
				.eventMask(snapshot.eventMask() == null ? null : snapshot.eventMask().shortValue())
				.engineLoad(snapshot.engineLoad())
				.accX(snapshot.accX())
				.accY(snapshot.accY())
				.gyroY(snapshot.gyroY())
				.batteryVoltage(snapshot.batteryVoltage())
				.accelPedalPosition(snapshot.accelPedalPosition())
				.build();
	}

	private record TripKey(String vehicleId, int tid) {
	}

}
