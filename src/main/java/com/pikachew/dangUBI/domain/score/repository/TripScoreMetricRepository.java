package com.pikachew.dangUBI.domain.score.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;

public interface TripScoreMetricRepository extends JpaRepository<TripScoreMetric, Long> {

	/** 아직 구간에 묶이지 않은 지표를 쌓는 순서(종료 시각, id)대로 */
	List<TripScoreMetric> findByDriverAndSegmentIsNullOrderByEndedAtAscIdAsc(Driver driver);

	/** 아직 리포트에 담기지 않은 지표 */
	List<TripScoreMetric> findByDriverAndReportIsNullOrderByEndedAtAscIdAsc(Driver driver);

}
