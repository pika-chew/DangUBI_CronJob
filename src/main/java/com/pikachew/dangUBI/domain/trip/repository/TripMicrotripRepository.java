package com.pikachew.dangUBI.domain.trip.repository;

import java.util.Collection;
import java.util.List;

import com.pikachew.dangUBI.domain.trip.entity.Trip;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripMicrotripRepository extends JpaRepository<TripMicrotrip, Long> {

	/** 주어진 키 중 이미 저장된 키만 돌려준다. */
	@Query("select m.idempotencyKey from TripMicrotrip m where m.idempotencyKey in :keys")
	List<String> findExistingIdempotencyKeys(@Param("keys") Collection<String> keys);

	List<TripMicrotrip> findByTripOrderByCollectedAt(Trip trip);

}
