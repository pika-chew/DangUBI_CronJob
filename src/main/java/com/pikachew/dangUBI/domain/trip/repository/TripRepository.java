package com.pikachew.dangUBI.domain.trip.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {

	Optional<Trip> findByVehicleIdAndTid(String vehicleId, int tid);

	/** 차량 목록 × tid 목록에 걸리는 trip. 정확한 (vehicleId, tid) 짝은 호출한 쪽에서 거른다 */
	List<Trip> findAllByVehicleIdInAndTidIn(Collection<String> vehicleIds, Collection<Integer> tids);

}
