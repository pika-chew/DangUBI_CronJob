package com.pikachew.dangUBI.domain.driver.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.driver.entity.Driver;

public interface DriverRepository extends JpaRepository<Driver, Long> {

	Optional<Driver> findByVehicleId(String vehicleId);

	List<Driver> findAllByVehicleIdIn(Collection<String> vehicleIds);

}
