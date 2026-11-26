package com.pikachew.dangUBI.domain.score.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;

public interface ScoreSegmentRepository extends JpaRepository<ScoreSegment, Long> {

	/** 차량의 마지막 구간 */
	Optional<ScoreSegment> findTopByVehicleIdOrderBySequenceNoDesc(String vehicleId);

	/** 반영 범위 후보: 최근 구간부터. 범위(최대 1,500km)를 채우기에 충분한 수만 가져온다 */
	List<ScoreSegment> findTop100ByVehicleIdOrderBySequenceNoDesc(String vehicleId);

}
