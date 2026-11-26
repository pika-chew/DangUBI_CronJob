package com.pikachew.dangUBI.batch.score.reader;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.score.config.ScoreProperties;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;

/**
 * scoreReportStep의 Reader: 아직 리포트에 담지 않은 지표가 있는 차량(VIN)을 VIN 순서로 넘긴다.
 * 페이지는 마지막으로 넘긴 VIN 다음부터(keyset) 읽는다. 다시 실행하면 리포트를 남기지 못한 차량만 다시 잡힌다.
 */
@Component
@StepScope
public class ScoreVehicleReader implements ItemReader<String> {

	private final TripScoreMetricRepository metricRepository;
	private final int pageSize;
	private final Deque<String> page = new ArrayDeque<>();
	/** 빈 문자열은 모든 VIN보다 앞이다 */
	private String lastVehicleId = "";
	private boolean exhausted;

	public ScoreVehicleReader(TripScoreMetricRepository metricRepository, ScoreProperties scoreProperties) {
		this.metricRepository = metricRepository;
		this.pageSize = scoreProperties.chunkSize();
	}

	@Override
	public String read() {
		if (page.isEmpty() && !exhausted) {
			page.addAll(metricRepository.findUnreportedVehicleIds(lastVehicleId, Limit.of(pageSize)));
			exhausted = page.size() < pageSize;
		}
		String next = page.poll();
		if (next != null) {
			lastVehicleId = next;
		}
		return next;
	}

}
