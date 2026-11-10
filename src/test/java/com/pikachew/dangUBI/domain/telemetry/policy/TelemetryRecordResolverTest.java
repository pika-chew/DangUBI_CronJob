package com.pikachew.dangUBI.domain.telemetry.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.domain.telemetry.exception.TelemetryHandler;
import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import org.junit.jupiter.api.Test;

/** 단위 테스트: 수집 메시지 한 줄 해석 규칙. */
public class TelemetryRecordResolverTest {

	public static final String TRIP_LINE = """
			{"vehicleId":"vehicle-001","ts":1793577600100,"ty":3,"schemaVersion":1,"idempotencyKey":"example-trip-1",\
			"pld":{"tid":354,"stt":1793574000000,"edt":1793577600000,"dis":30000,"hsts":90,"mesp":30,"dtvt":3600}}""";

	public static final String MICROTRIP_LINE = """
			{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"example-microtrip-1",\
			"pld":{"tid":354,"clt":1793575800000,"sp":70,"em":"28"}}""";

	private final TelemetryRecordResolver resolver = new TelemetryRecordResolver();

	@Test
	void ty3_줄을_TripRecord로_해석한다() {
		TripRecord trip = (TripRecord) resolver.resolve(TRIP_LINE);

		assertThat(trip.vehicleId()).isEqualTo("vehicle-001");
		assertThat(trip.idempotencyKey()).isEqualTo("example-trip-1");
		assertThat(trip.receivedAt()).isEqualTo(Instant.ofEpochMilli(1793577600100L));
		assertThat(trip.tid()).isEqualTo(354);
		assertThat(trip.startedAt()).isEqualTo(Instant.ofEpochMilli(1793574000000L));
		assertThat(trip.endedAt()).isEqualTo(Instant.ofEpochMilli(1793577600000L));
		assertThat(trip.distance()).isEqualTo(30000);
		assertThat(trip.maxSpeed()).isEqualTo(90);
		assertThat(trip.avgSpeed()).isEqualTo(30);
		assertThat(trip.drivingTime()).isEqualTo(3600);
	}

	@Test
	void 문서상_필수여도_실제로_빠진_필드는_null이다() {
		TripRecord trip = (TripRecord) resolver.resolve(TRIP_LINE);

		assertThat(trip.totalDistance()).isNull();
		assertThat(trip.fuelConsumption()).isNull();
		assertThat(trip.startLat()).isNull();
	}

	@Test
	void ty4_줄을_MicrotripRecord로_해석한다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(MICROTRIP_LINE);

		assertThat(microtrip.tid()).isEqualTo(354);
		assertThat(microtrip.collectedAt()).isEqualTo(Instant.ofEpochMilli(1793575800000L));
		assertThat(microtrip.speed()).isEqualTo(70);
		assertThat(microtrip.lat()).isNull();
	}

	@Test
	void em은_16진으로_해석한다_28은_급유턴과_급가속이다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(MICROTRIP_LINE);

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
		assertThat(microtrip.eventMask() & (1 << 3)).isNotZero();
		assertThat(microtrip.eventMask() & (1 << 5)).isNotZero();
	}

	@Test
	void em이_숫자로_와도_16진으로_해석한다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"em\":28"));

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
	}

	@Test
	void em이_16진이_아니면_그_필드만_null이다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"em\":\"XZ\",\"sp\":70"));

		assertThat(microtrip.eventMask()).isNull();
		assertThat(microtrip.speed()).isEqualTo(70);
	}

	@Test
	void xyz는_쉼표로_나눠_세_값으로_해석한다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"xyz\":\"12,-3, 7\""));

		assertThat(microtrip.accX()).isEqualTo(12);
		assertThat(microtrip.accY()).isEqualTo(-3);
		assertThat(microtrip.gyroY()).isEqualTo(7);
	}

	@Test
	void xyz_형식이_틀리면_세_값이_모두_null이다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"xyz\":\"12,abc,7\""));

		assertThat(microtrip.accX()).isNull();
		assertThat(microtrip.accY()).isNull();
		assertThat(microtrip.gyroY()).isNull();
	}

	@Test
	void 숫자_문자열도_숫자로_받는다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"sp\":\"72\""));

		assertThat(microtrip.speed()).isEqualTo(72);
	}

	@Test
	void 선택_필드_형식이_틀리면_그_필드만_null이다() {
		MicrotripRecord microtrip = (MicrotripRecord) resolver.resolve(microtripPld("\"sp\":\"fast\",\"rpm\":2000"));

		assertThat(microtrip.speed()).isNull();
		assertThat(microtrip.rpm()).isEqualTo(2000);
	}

	@Test
	void usm은_읽지_않는다() {
		String line = TRIP_LINE.replace("\"tid\":354,", "\"tid\":354,\"usm\":1012345678,");

		TripRecord trip = (TripRecord) resolver.resolve(line);

		assertThat(trip.toString()).doesNotContain("1012345678");
	}

	@Test
	void JSON이_아니면_버린다() {
		assertRejected("{not json", ErrorStatus.TELEMETRY_INVALID_JSON);
		assertRejected("[1,2]", ErrorStatus.TELEMETRY_INVALID_JSON);
	}

	@Test
	void 지원하지_않는_ty는_버린다() {
		assertRejected(TRIP_LINE.replace("\"ty\":3", "\"ty\":9"), ErrorStatus.TELEMETRY_UNSUPPORTED_TYPE, "9");
	}

	@Test
	void 지원하지_않는_schemaVersion은_버린다() {
		assertRejected(TRIP_LINE.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
				ErrorStatus.TELEMETRY_UNSUPPORTED_SCHEMA_VERSION, "2");
	}

	@Test
	void 키_필드가_없으면_버린다() {
		assertRejected(TRIP_LINE.replace("\"vehicleId\":\"vehicle-001\",", ""), ErrorStatus.TELEMETRY_MISSING_FIELD, "vehicleId");
		assertRejected(TRIP_LINE.replace("\"idempotencyKey\":\"example-trip-1\",", ""), ErrorStatus.TELEMETRY_MISSING_FIELD, "idempotencyKey");
		assertRejected(TRIP_LINE.replace("\"tid\":354,", ""), ErrorStatus.TELEMETRY_MISSING_FIELD, "tid");
		assertRejected(MICROTRIP_LINE.replace("\"clt\":1793575800000,", ""), ErrorStatus.TELEMETRY_MISSING_FIELD, "clt");
	}

	@Test
	void pld가_없으면_버린다() {
		assertRejected("""
				{"vehicleId":"v","ts":1,"ty":3,"schemaVersion":1,"idempotencyKey":"k"}""",
				ErrorStatus.TELEMETRY_MISSING_FIELD, "pld");
	}

	private void assertRejected(String line, ErrorStatus status, String... details) {
		assertThatThrownBy(() -> resolver.resolve(line))
				.isInstanceOf(TelemetryHandler.class)
				.satisfies(e -> assertThat(((TelemetryHandler) e).getErrorCode()).isEqualTo(status))
				.satisfies(e -> {
					for (String detail : details) {
						assertThat(e.getMessage()).contains(detail);
					}
				});
	}

	private static String microtripPld(String extraFields) {
		return """
				{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"k",\
				"pld":{"tid":354,"clt":1793575800000,%s}}""".formatted(extraFields);
	}

}
