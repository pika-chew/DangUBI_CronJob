package com.pikachew.dangUBI.domain.trip.repository;

import java.util.Optional;

import com.pikachew.dangUBI.domain.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, Long> {

	Optional<Trip> findByVehicleIdAndTid(String vehicleId, int tid);

}
