package com.pikachew.dangUBI.domain.score.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.domain.score.model.DrivingEventCounts;
import com.pikachew.dangUBI.domain.score.model.DrivingEventResult;
import com.pikachew.dangUBI.domain.trip.entity.TripMicrotrip;

/** 단위 테스트: 이벤트 묶음 판정, 연속 기동 묶기, 과속거리, KST 시간대 */
class DrivingEventPolicyTest {

	private static final Duration GAP = Duration.ofSeconds(4);
	/** KST 10:00 (주간) */
	private static final Instant DAY = Instant.parse("2026-11-25T01:00:00Z");

	@Test
	void 급출발만_급가속만_둘_다_켜진_경우_모두_급가속_1건이다() {
		assertThat(total(mt(0, 0x01)).accelCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x20)).accelCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x21)).accelCount()).isEqualTo(1);
	}

	@Test
	void 급감속과_급정지는_급감속_묶음이다() {
		assertThat(total(mt(0, 0x10)).brakeCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x40)).brakeCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x50)).brakeCount()).isEqualTo(1);
	}

	@Test
	void 좌회전_우회전_유턴은_급회전_묶음이다() {
		assertThat(total(mt(0, 0x02)).turnCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x04)).turnCount()).isEqualTo(1);
		assertThat(total(mt(0, 0x0E)).turnCount()).isEqualTo(1);
	}

	@Test
	void 같은_주기에_여러_묶음이_켜지면_묶음마다_1건이다() {
		DrivingEventCounts counts = total(mt(0, 0x28));

		assertThat(counts.accelCount()).isEqualTo(1);
		assertThat(counts.turnCount()).isEqualTo(1);
		assertThat(counts.brakeCount()).isZero();
	}

	@Test
	void 간격이_4초_이하로_이어지면_한_번의_기동이다() {
		assertThat(total(mt(0, 0x20), mt(2, 0x20), mt(6, 0x20)).accelCount()).isEqualTo(1);
	}

	@Test
	void 간격이_4초를_넘으면_다른_기동이다() {
		assertThat(total(mt(0, 0x20), mt(5, 0x20)).accelCount()).isEqualTo(2);
	}

	@Test
	void 사이에_묶음이_꺼진_주기가_있으면_다른_기동이다() {
		assertThat(total(mt(0, 0x20), mt(2, 0x00), mt(4, 0x20)).accelCount()).isEqualTo(2);
	}

	@Test
	void 급출발_다음_주기의_급가속은_같은_묶음이라_1건이다() {
		assertThat(total(mt(0, 0x01), mt(2, 0x20)).accelCount()).isEqualTo(1);
	}

	@Test
	void 다른_묶음은_서로_끊지_않는다() {
		DrivingEventCounts counts = total(mt(0, 0x20), mt(2, 0x30), mt(4, 0x20));

		assertThat(counts.accelCount()).isEqualTo(1);
		assertThat(counts.brakeCount()).isEqualTo(1);
	}

	@Test
	void 입력_순서와_상관없이_수집_시각_순서로_판정한다() {
		assertThat(total(mt(6, 0x20), mt(0, 0x20), mt(2, 0x20)).accelCount()).isEqualTo(1);
	}

	@Test
	void 과속거리는_과속_주기마다_속도_곱하기_2초다() {
		DrivingEventCounts counts = total(mt(0, 0x80, 72), mt(2, 0x80, 72), mt(4, 0x80, 90));

		assertThat(counts.overspeedKm()).isCloseTo((72 + 72 + 90) * 2 / 3600.0, within(1e-9));
	}

	@Test
	void 속도가_없는_과속_주기는_과속거리에서_뺀다() {
		assertThat(total(mt(0, 0x80, null)).overspeedKm()).isZero();
	}

	@Test
	void em이_없는_주기는_평가하지_않고_기동을_끊는다() {
		DrivingEventResult result = evaluate(mt(0, 0x20), mt(2, null), mt(4, 0x20));

		assertThat(result.unevaluatedCount()).isEqualTo(1);
		assertThat(result.total().accelCount()).isEqualTo(2);
	}

	@Test
	void 이벤트를_KST_시간대로_나눈다() {
		// 2026-11-24T16:30Z = KST 01:30
		DrivingEventResult result = evaluate(
				mt(Instant.parse("2026-11-24T16:30:00Z"), 0x10, 50),
				mt(Instant.parse("2026-11-24T16:30:10Z"), 0x80, 90),
				mt(DAY, 0x20, 50));

		assertThat(result.countsByKstHour()).containsOnlyKeys(1, 10);
		assertThat(result.countsByKstHour().get(1).brakeCount()).isEqualTo(1);
		assertThat(result.countsByKstHour().get(1).overspeedKm()).isCloseTo(90 * 2 / 3600.0, within(1e-9));
		assertThat(result.countsByKstHour().get(10).accelCount()).isEqualTo(1);
	}

	@Test
	void 시간대를_넘어_이어진_기동은_첫_주기의_시간대에_센다() {
		// KST 00:59:58에 시작해 01:00:02까지 이어진 급감속
		DrivingEventResult result = evaluate(
				mt(Instant.parse("2026-11-24T15:59:58Z"), 0x10, 50),
				mt(Instant.parse("2026-11-24T16:00:00Z"), 0x10, 50),
				mt(Instant.parse("2026-11-24T16:00:02Z"), 0x10, 50));

		assertThat(result.countsByKstHour()).containsOnlyKeys(0);
		assertThat(result.total().brakeCount()).isEqualTo(1);
	}

	@Test
	void microtrip이_없으면_모두_0이다() {
		DrivingEventResult result = evaluate();

		assertThat(result.countsByKstHour()).isEmpty();
		assertThat(result.total()).isEqualTo(new DrivingEventCounts(0, 0, 0, 0));
	}

	private static DrivingEventCounts total(TripMicrotrip... microtrips) {
		return evaluate(microtrips).total();
	}

	private static DrivingEventResult evaluate(TripMicrotrip... microtrips) {
		return DrivingEventPolicy.evaluate(new ArrayList<>(List.of(microtrips)), GAP);
	}

	private static TripMicrotrip mt(int secondsFromStart, Integer eventMask) {
		return mt(DAY.plusSeconds(secondsFromStart), eventMask, 50);
	}

	private static TripMicrotrip mt(int secondsFromStart, Integer eventMask, Integer speed) {
		return mt(DAY.plusSeconds(secondsFromStart), eventMask, speed);
	}

	private static TripMicrotrip mt(Instant collectedAt, Integer eventMask, Integer speed) {
		return TripMicrotrip.builder()
				.idempotencyKey("mt-" + collectedAt)
				.collectedAt(collectedAt)
				.speed(speed)
				.eventMask(eventMask == null ? null : eventMask.shortValue())
				.build();
	}

}
