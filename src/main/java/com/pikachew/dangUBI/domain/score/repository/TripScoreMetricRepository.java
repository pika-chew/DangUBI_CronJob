package com.pikachew.dangUBI.domain.score.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pikachew.dangUBI.domain.driver.entity.Driver;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.trip.entity.TripStatus;

public interface TripScoreMetricRepository extends JpaRepository<TripScoreMetric, Long> {

	/** 아직 지표가 없는 trip id를 afterId 다음부터 id 순서로. 처리한 trip은 지표가 생겨 다음 조회에서 빠진다 */
	@Query("""
			select t.id from Trip t
			where t.status = :status and t.id > :afterId
			  and not exists (select m.id from TripScoreMetric m where m.trip = t)
			order by t.id""")
	List<Long> findUnscoredTripIds(@Param("status") TripStatus status, @Param("afterId") long afterId, Limit limit);

	/** 아직 지표를 리포트에 담지 않은 운전자 id를 afterId 다음부터 id 순서로 */
	@Query("""
			select distinct m.driver.id from TripScoreMetric m
			where m.report is null and m.driver.id > :afterId
			order by m.driver.id""")
	List<Long> findUnreportedDriverIds(@Param("afterId") long afterId, Limit limit);

	/** 아직 구간에 묶이지 않은 지표를 쌓는 순서(종료 시각, id)대로 */
	List<TripScoreMetric> findByDriverAndSegmentIsNullOrderByEndedAtAscIdAsc(Driver driver);

	/** 아직 리포트에 담기지 않은 지표 */
	List<TripScoreMetric> findByDriverAndReportIsNullOrderByEndedAtAscIdAsc(Driver driver);

}
