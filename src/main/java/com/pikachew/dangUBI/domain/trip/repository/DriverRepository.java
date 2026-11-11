package com.pikachew.dangUBI.domain.trip.repository;

import java.util.Optional;

import com.pikachew.dangUBI.domain.trip.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, Long> {

	Optional<Driver> findByVehicleId(String vehicleId);

}
