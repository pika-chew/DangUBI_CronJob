package com.pikachew.dangUBI.domain.score.policy;

import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_ACCEL_MASK;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_BRAKE_MASK;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_MICROTRIP_INTERVAL_SECONDS;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_OVERSPEED_MASK;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_SECONDS_PER_HOUR;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_TURN_MASK;
import static com.pikachew.dangUBI.common.consts.ScoreStaticValues.SCORE_ZONE;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.pikachew.dangUBI.domain.score.model.DrivingEventCounts;
import com.pikachew.dangUBI.domain.score.model.DrivingEventResult;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;

/**
 * trip 하나의 microtrip에서 이벤트를 판정하고 센다 (docs/score-spec.md 3·4장).
 *
 * - 묶음 안의 비트가 하나라도 켜지면 그 묶음의 이벤트다.
 * - 같은 묶음이 켜진 microtrip이 이어지고 앞 건과의 clt 간격이 maneuverGap 이하면 한 번의 기동으로 센다.
 *   사이에 묶음이 꺼진 주기나 em이 없는 주기가 있으면 끊긴다.
 * - 과속거리는 과속 비트가 켜진 주기마다 sp(km/h) × 전송 주기를 더한다. sp가 없는 주기는 뺀다.
 * - em이 없는 주기는 평가하지 않는다.
 */
public final class DrivingEventPolicy {

	private DrivingEventPolicy() {
	}

	public static DrivingEventResult evaluate(List<TripMicrotrip> microtrips, Duration maneuverGap) {
		List<TripMicrotrip> sorted = microtrips.stream()
				.sorted(Comparator.comparing(TripMicrotrip::getCollectedAt))
				.toList();

		Map<Integer, DrivingEventCounts> countsByHour = new HashMap<>();
		int unevaluated = 0;
		TripMicrotrip previous = null;
		for (TripMicrotrip current : sorted) {
			if (current.getEventMask() == null) {
				unevaluated++;
				previous = current;
				continue;
			}
			int hour = kstHour(current.getCollectedAt());
			DrivingEventCounts counts = new DrivingEventCounts(
					overspeedKm(current),
					startsManeuver(previous, current, SCORE_ACCEL_MASK, maneuverGap) ? 1 : 0,
					startsManeuver(previous, current, SCORE_BRAKE_MASK, maneuverGap) ? 1 : 0,
					startsManeuver(previous, current, SCORE_TURN_MASK, maneuverGap) ? 1 : 0);
			if (!isEmpty(counts)) {
				countsByHour.merge(hour, counts, DrivingEventCounts::plus);
			}
			previous = current;
		}
		return new DrivingEventResult(countsByHour, unevaluated);
	}

	/** 이 주기에서 묶음이 켜졌고, 바로 앞 주기가 같은 기동이 아니면 새 기동이다 */
	private static boolean startsManeuver(TripMicrotrip previous, TripMicrotrip current, int groupMask, Duration maneuverGap) {
		if (!isOn(current, groupMask)) {
			return false;
		}
		if (previous == null || !isOn(previous, groupMask)) {
			return true;
		}
		return Duration.between(previous.getCollectedAt(), current.getCollectedAt()).compareTo(maneuverGap) > 0;
	}

	private static double overspeedKm(TripMicrotrip microtrip) {
		if (!isOn(microtrip, SCORE_OVERSPEED_MASK) || microtrip.getSpeed() == null) {
			return 0;
		}
		return microtrip.getSpeed() * SCORE_MICROTRIP_INTERVAL_SECONDS / SCORE_SECONDS_PER_HOUR;
	}

	private static boolean isOn(TripMicrotrip microtrip, int groupMask) {
		Short eventMask = microtrip.getEventMask();
		return eventMask != null && (eventMask & groupMask) != 0;
	}

	private static boolean isEmpty(DrivingEventCounts counts) {
		return counts.overspeedKm() == 0 && counts.accelCount() == 0 && counts.brakeCount() == 0 && counts.turnCount() == 0;
	}

	private static int kstHour(Instant instant) {
		return instant.atZone(SCORE_ZONE).getHour();
	}

}
