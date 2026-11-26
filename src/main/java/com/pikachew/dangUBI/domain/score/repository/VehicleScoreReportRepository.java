package com.pikachew.dangUBI.domain.score.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.score.entity.VehicleScoreReport;

public interface VehicleScoreReportRepository extends JpaRepository<VehicleScoreReport, Long> {

	/** 차량의 직전 리포트 */
	Optional<VehicleScoreReport> findTopByVehicleIdOrderByIdDesc(String vehicleId);

}
