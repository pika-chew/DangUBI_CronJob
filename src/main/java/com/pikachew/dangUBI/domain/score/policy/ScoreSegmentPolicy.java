package com.pikachew.dangUBI.domain.score.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.pikachew.dangUBI.domain.score.entity.ScoreSegment;
import com.pikachew.dangUBI.domain.score.entity.TripScoreMetric;

/**
 * 구간을 닫는 규칙과 반영 범위를 고르는 규칙 (docs/score-spec.md 5·6장).
 */
public final class ScoreSegmentPolicy {

	private ScoreSegmentPolicy() {
	}

	/**
	 * 쌓는 순서대로 놓인 지표를 누적해, 구간 거리 이상이 되는 지표까지를 구간 하나로 묶는다. trip은 자르지 않는다.
	 * 구간 거리를 채우지 못한 나머지는 돌려주지 않는다(다음 실행까지 남는다).
	 *
	 * @return 닫을 구간마다 그 구간에 들어갈 지표 목록
	 */
	public static List<List<TripScoreMetric>> closeSegments(List<TripScoreMetric> orderedMetrics, long segmentDistanceM) {
		List<List<TripScoreMetric>> closed = new ArrayList<>();
		List<TripScoreMetric> current = new ArrayList<>();
		long accumulated = 0;
		for (TripScoreMetric metric : orderedMetrics) {
			current.add(metric);
			accumulated += metric.getTally().getDistanceM();
			if (accumulated >= segmentDistanceM) {
				closed.add(current);
				current = new ArrayList<>();
				accumulated = 0;
			}
		}
		return closed;
	}

	/**
	 * 최근 구간부터 반영 범위 거리를 채울 때까지 고른다. 범위를 넘기게 하는 구간까지 넣는다.
	 *
	 * @param newestFirst 최근 구간이 앞에 오는 목록
	 * @return 고른 구간을 오래된 순서로
	 */
	public static List<ScoreSegment> window(List<ScoreSegment> newestFirst, long windowDistanceM) {
		List<ScoreSegment> selected = new ArrayList<>();
		long accumulated = 0;
		for (ScoreSegment segment : newestFirst) {
			if (accumulated >= windowDistanceM) {
				break;
			}
			selected.add(segment);
			accumulated += segment.getTally().getDistanceM();
		}
		Collections.reverse(selected);
		return selected;
	}

}
