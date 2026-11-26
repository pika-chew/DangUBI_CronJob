package com.pikachew.dangUBI.domain.score.model;

import java.util.Map;

/**
 * trip 하나의 이벤트 판정 결과.
 *
 * 야간 감점률은 설정으로 바뀔 수 있어, 감점률을 곱하지 않은 KST 시간대(0–23)별 원값으로 둔다.
 * 기동은 첫 주기의 시간대, 과속거리는 주기마다 그 주기의 시간대에 들어간다.
 *
 * @param countsByKstHour  이벤트가 있었던 시간대만 담는다
 * @param unevaluatedCount em이 없어 평가하지 못한 microtrip 수
 */
public record DrivingEventResult(
		Map<Integer, DrivingEventCounts> countsByKstHour,
		int unevaluatedCount
) {

	public DrivingEventResult {
		countsByKstHour = Map.copyOf(countsByKstHour);
	}

	public DrivingEventCounts total() {
		return countsByKstHour.values().stream()
				.reduce(new DrivingEventCounts(0, 0, 0, 0), DrivingEventCounts::plus);
	}

}
