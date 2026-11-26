package com.pikachew.dangUBI.batch.score.writer;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;
import com.pikachew.dangUBI.domain.score.repository.TripScoreMetricRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** scoreMetricStep의 Writer: trip 지표를 저장한다. chunk 트랜잭션 안에서 실행된다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripScoreMetricWriter implements ItemWriter<TripScoreMetric> {

	private final TripScoreMetricRepository metricRepository;

	@Override
	public void write(Chunk<? extends TripScoreMetric> chunk) {
		metricRepository.saveAll(chunk.getItems());
		log.info("score metric chunk: trip 지표 {}건 저장", chunk.size());
	}

}
