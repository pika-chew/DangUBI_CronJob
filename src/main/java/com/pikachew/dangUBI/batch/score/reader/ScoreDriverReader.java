package com.pikachew.dangUBI.batch.score.reader;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_CHUNK_SIZE_PROPERTY;

import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;

/**
 * scoreReportStep의 Reader: 아직 리포트에 담지 않은 지표가 있는 운전자 id를 id 순서로 넘긴다.
 * 페이지는 마지막으로 넘긴 id 다음부터(keyset) 읽는다. 다시 실행하면 리포트를 남기지 못한 운전자만 다시 잡힌다.
 */
@Component
@StepScope
public class ScoreDriverReader implements ItemReader<Long> {

	private final TripScoreMetricRepository metricRepository;
	private final int pageSize;
	private final Deque<Long> page = new ArrayDeque<>();
	private long lastId;
	private boolean exhausted;

	public ScoreDriverReader(TripScoreMetricRepository metricRepository, @Value(SCORE_CHUNK_SIZE_PROPERTY) int pageSize) {
		this.metricRepository = metricRepository;
		this.pageSize = pageSize;
	}

	@Override
	public Long read() {
		if (page.isEmpty() && !exhausted) {
			page.addAll(metricRepository.findUnreportedDriverIds(lastId, Limit.of(pageSize)));
			exhausted = page.size() < pageSize;
		}
		Long next = page.poll();
		if (next != null) {
			lastId = next;
		}
		return next;
	}

}
