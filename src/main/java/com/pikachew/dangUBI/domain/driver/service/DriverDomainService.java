package com.pikachew.dangUBI.domain.driver.service;

import com.pikachew.dangUBI.domain.driver.entity.Driver;

public interface DriverDomainService {

	/**
	 * 차량의 운전자를 돌려준다. 처음 보는 차량이면 운전자를 만든다. 지금은 차량 1대 = 운전자 1명이다.
	 * 트랜잭션은 호출하는 쪽이 연다.
	 */
	Driver getOrCreate(String vehicleId);

}
