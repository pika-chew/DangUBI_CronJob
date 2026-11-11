package com.pikachew.dangUBI.domain.trip.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.trip.entity.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {

	Optional<Trip> findByVehicleIdAndTid(String vehicleId, int tid);

}
