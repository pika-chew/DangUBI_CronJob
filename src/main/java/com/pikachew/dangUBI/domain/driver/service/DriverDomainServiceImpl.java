package com.pikachew.dangUBI.domain.driver.service;

import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.common.annotation.DomainService;
import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;

import lombok.RequiredArgsConstructor;

@Transactional
@DomainService
@RequiredArgsConstructor
public class DriverDomainServiceImpl implements DriverDomainService {

	private final DriverRepository driverRepository;

	@Override
	public Driver getOrCreate(String vehicleId) {
		return driverRepository.findByVehicleId(vehicleId)
				.orElseGet(() -> driverRepository.save(Driver.ofVehicle(vehicleId)));
	}

}
