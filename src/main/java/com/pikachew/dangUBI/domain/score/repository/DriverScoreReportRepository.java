package com.pikachew.dangUBI.domain.score.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.score.entity.DriverScoreReport;

public interface DriverScoreReportRepository extends JpaRepository<DriverScoreReport, Long> {

	/** 운전자의 직전 리포트 */
	Optional<DriverScoreReport> findTopByDriverOrderByIdDesc(Driver driver);

}
