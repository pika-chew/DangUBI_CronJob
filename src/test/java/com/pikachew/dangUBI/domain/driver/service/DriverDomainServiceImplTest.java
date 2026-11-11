package com.pikachew.dangUBI.domain.driver.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.driver.repository.DriverRepository;

/** DB 통합 테스트: 차량별 운전자 찾기·만들기 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DriverDomainServiceImplTest {

	@Autowired
	private DriverDomainService driverDomainService;

	@Autowired
	private DriverRepository driverRepository;

	@Test
	void 처음_보는_차량이면_운전자를_만든다() {
		Driver driver = driverDomainService.getOrCreate("vehicle-001");

		assertThat(driver.getId()).isNotNull();
		assertThat(driver.getVehicleId()).isEqualTo("vehicle-001");
		assertThat(driverRepository.count()).isEqualTo(1);
	}

	@Test
	void 이미_있는_차량이면_같은_운전자를_돌려준다() {
		Driver first = driverDomainService.getOrCreate("vehicle-001");

		Driver second = driverDomainService.getOrCreate("vehicle-001");

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(driverRepository.count()).isEqualTo(1);
	}

	@Test
	void 생성_시각과_수정_시각이_채워진다() {
		Driver driver = driverDomainService.getOrCreate("vehicle-001");
		driverRepository.flush();

		assertThat(driver.getCreatedAt()).isNotNull();
		assertThat(driver.getUpdatedAt()).isNotNull();
	}

}
