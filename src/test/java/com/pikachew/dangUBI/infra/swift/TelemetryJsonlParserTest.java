package com.pikachew.dangUBI.infra.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.domain.trip.model.MicrotripSnapshot;
import com.pikachew.dangUBI.domain.trip.model.TripSnapshot;

/** 단위 테스트: gz JSONL 수집 파일과 한 줄 해석 규칙 */
public class TelemetryJsonlParserTest {

	public static final String TRIP_LINE = """
			{"vehicleId":"vehicle-001","ts":1793577600100,"ty":3,"schemaVersion":1,"idempotencyKey":"example-trip-1",\
			"pld":{"tid":354,"stt":1793574000000,"edt":1793577600000,"dis":30000,"hsts":90,"mesp":30,"dtvt":3600}}""";

	/** Lambda를 거친 실제 ty=3 출력 (2026-11-25) */
	private static final String LAMBDA_TRIP_LINE = """
			{"vehicleId":"0DS614RB2TT925001","ts":1795566000000,"ty":3,"schemaVersion":1,\
			"idempotencyKey":"fc430f29e76652d76176f645b54b311eaa25392345894f9ce04321a845ab171d",\
			"pld":{"tid":26112501,"stt":1795564800000,"edt":1795566000000,"dis":12000,"hsts":65,"mesp":36,"dtvt":1170}}""";

	public static final String MICROTRIP_LINE = """
			{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"example-microtrip-1",\
			"pld":{"tid":354,"clt":1793575800000,"sp":70,"em":"28"}}""";

	/** Lambda를 거친 실제 ty=4 출력 (2026-11-26). tid가 int 범위를 넘고 sp가 소수다 */
	private static final String LAMBDA_MICROTRIP_LINE = """
			{"vehicleId":"0DSN7PNB6KT000009","ts":1795643508000,"ty":4,"schemaVersion":1,\
			"idempotencyKey":"4399a0467bd23a946b9220205ed8385e471a7cfb577084493f39cb85e7ea0415",\
			"pld":{"tid":30836460003626,"clt":1795643508000,"sp":42.392873068822745,"em":0}}""";

	private static final String KEY ="firehose/processed/year=2026/month=11/day=10/hour=03/a.jsonl.gz";

	// ---- 한 줄 ----

	@Test
	void ty3_줄을_TripSnapshot으로_해석한다() {
		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(TRIP_LINE);

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
	void Lambda가_보낸_실제_ty3_줄을_해석한다() {
		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(LAMBDA_TRIP_LINE);

		assertThat(trip.vehicleId()).isEqualTo("0DS614RB2TT925001");
		assertThat(trip.tid()).isEqualTo(26112501);
		assertThat(trip.receivedAt()).isEqualTo(Instant.parse("2026-11-25T00:20:00Z"));
		assertThat(trip.startedAt()).isEqualTo(Instant.parse("2026-11-25T00:00:00Z"));
		assertThat(trip.endedAt()).isEqualTo(Instant.parse("2026-11-25T00:20:00Z"));
		assertThat(trip.distance()).isEqualTo(12000);
		assertThat(trip.maxSpeed()).isEqualTo(65);
		assertThat(trip.avgSpeed()).isEqualTo(36);
		assertThat(trip.drivingTime()).isEqualTo(1170);
	}

	@Test
	void 소수인_ty3_요약값은_반올림한다() {
		String line = TRIP_LINE.replace("\"dis\":30000,\"hsts\":90,\"mesp\":30,\"dtvt\":3600",
				"\"dis\":29381.570831045887,\"hsts\":100.00000000000001,\"mesp\":40.99754069448263,\"dtvt\":2471.952782064332");

		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(line);

		assertThat(trip.distance()).isEqualTo(29382);
		assertThat(trip.maxSpeed()).isEqualTo(100);
		assertThat(trip.avgSpeed()).isEqualTo(41);
		assertThat(trip.drivingTime()).isEqualTo(2472);
	}

	@Test
	void 빠진_요약_필드는_null이다() {
		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(TRIP_LINE.replace(",\"hsts\":90", ""));

		assertThat(trip.maxSpeed()).isNull();
		assertThat(trip.distance()).isEqualTo(30000);
	}

	@Test
	void 저장하지_않는_ty3_필드는_읽지_않는다() {
		String line = TRIP_LINE.replace("\"tid\":354,", "\"tid\":354,\"fc\":4321,\"stlat\":375665,\"fwv\":9876,");

		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(line);

		assertThat(trip.toString()).doesNotContain("4321", "375665", "9876");
	}

	@Test
	void ty4_줄을_MicrotripSnapshot으로_해석한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(MICROTRIP_LINE);

		assertThat(microtrip.tid()).isEqualTo(354);
		assertThat(microtrip.collectedAt()).isEqualTo(Instant.ofEpochMilli(1793575800000L));
		assertThat(microtrip.speed()).isEqualTo(70);
		assertThat(microtrip.eventMask()).isEqualTo(0x28);
	}

	@Test
	void int_범위를_넘는_tid도_받는다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(LAMBDA_MICROTRIP_LINE);

		assertThat(microtrip.vehicleId()).isEqualTo("0DSN7PNB6KT000009");
		assertThat(microtrip.tid()).isEqualTo(30836460003626L);
	}

	@Test
	void 소수인_sp는_반올림한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(LAMBDA_MICROTRIP_LINE);

		assertThat(microtrip.speed()).isEqualTo(42);
		assertThat(microtrip.eventMask()).isZero();
	}

	@Test
	void 점수에_쓰지_않는_microtrip_필드는_읽지_않는다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(
				microtripPld("\"sp\":70,\"lat\":37.5665,\"vv\":12.6,\"xyz\":\"0.12,-0.05,0.98\",\"rpm\":2000"));

		assertThat(microtrip.speed()).isEqualTo(70);
		assertThat(microtrip.toString()).doesNotContain("37.5665", "12.6", "0.98", "2000");
	}

	@Test
	void em은_16진으로_해석한다_28은_급유턴과_급가속이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(MICROTRIP_LINE);

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
		assertThat(microtrip.eventMask() & (1 << 3)).isNotZero();
		assertThat(microtrip.eventMask() & (1 << 5)).isNotZero();
	}

	@Test
	void em이_숫자로_오면_10진으로_해석한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":40"));

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
	}

	@Test
	void 숫자_em_128은_bit7_과속이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":128"));

		assertThat(microtrip.eventMask()).isEqualTo(1 << 7);
	}

	@Test
	void 정의된_비트를_넘는_em은_그_필드만_null이다() {
		assertThat(((MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":256"))).eventMask()).isNull();
		assertThat(((MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":-1"))).eventMask()).isNull();
		assertThat(((MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":\"100\""))).eventMask()).isNull();
	}

	@Test
	void em이_16진이_아니면_그_필드만_null이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":\"XZ\",\"sp\":70"));

		assertThat(microtrip.eventMask()).isNull();
		assertThat(microtrip.speed()).isEqualTo(70);
	}

	@Test
	void 숫자_문자열도_숫자로_받는다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"sp\":\"72\""));

		assertThat(microtrip.speed()).isEqualTo(72);
	}

	@Test
	void 선택_필드_형식이_틀리면_그_필드만_null이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"sp\":\"fast\",\"em\":\"28\""));

		assertThat(microtrip.speed()).isNull();
		assertThat(microtrip.eventMask()).isEqualTo(0x28);
	}

	@Test
	void usm은_읽지_않는다() {
		String line = TRIP_LINE.replace("\"tid\":354,", "\"tid\":354,\"usm\":1012345678,");

		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(line);

		assertThat(trip.toString()).doesNotContain("1012345678");
	}

	@Test
	void JSON이_아니면_버린다() {
		assertRejected("{not json", "JSON");
		assertRejected("[1,2]", "JSON");
	}

	@Test
	void 지원하지_않는_ty는_버린다() {
		assertRejected(TRIP_LINE.replace("\"ty\":3", "\"ty\":9"), "ty: 9");
	}

	@Test
	void 지원하지_않는_schemaVersion은_버린다() {
		assertRejected(TRIP_LINE.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "schemaVersion: 2");
	}

	@Test
	void 키_필드가_없으면_버린다() {
		assertRejected(TRIP_LINE.replace("\"vehicleId\":\"vehicle-001\",", ""), "vehicleId");
		assertRejected(TRIP_LINE.replace("\"idempotencyKey\":\"example-trip-1\",", ""), "idempotencyKey");
		assertRejected(TRIP_LINE.replace("\"tid\":354,", ""), "tid");
		assertRejected(MICROTRIP_LINE.replace("\"clt\":1793575800000,", ""), "clt");
	}

	@Test
	void pld가_없으면_버린다() {
		assertRejected("""
				{"vehicleId":"v","ts":1,"ty":3,"schemaVersion":1,"idempotencyKey":"k"}""", "pld");
	}

	// ---- 파일 ----

	@Test
	void 파일의_trip과_microtrip을_나눠_담는다() {
		ParsedTelemetryFile parsed = TelemetryJsonlParser.parse(KEY, gzip(TRIP_LINE, MICROTRIP_LINE,
				MICROTRIP_LINE.replace("example-microtrip-1", "example-microtrip-2")));

		assertThat(parsed.objectKey()).isEqualTo(KEY);
		assertThat(parsed.trips()).hasSize(1);
		assertThat(parsed.microtrips()).hasSize(2);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void 해석하지_못한_줄은_건너뛰고_센다() {
		ParsedTelemetryFile parsed = TelemetryJsonlParser.parse(KEY,
				gzip(MICROTRIP_LINE, "{broken", TRIP_LINE.replace("\"ty\":3", "\"ty\":9")));

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isEqualTo(2);
	}

	@Test
	void 빈_줄은_세지_않는다() {
		ParsedTelemetryFile parsed = TelemetryJsonlParser.parse(KEY, gzip("", MICROTRIP_LINE, "   ", ""));

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void gz가_깨졌으면_파일_형식_예외를_던진다() {
		InputStream notGzip = new ByteArrayInputStream("plain text".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> TelemetryJsonlParser.parse(KEY, notGzip))
				.isInstanceOf(TelemetryFileFormatException.class)
				.hasMessageContaining(KEY);
	}

	@Test
	void 빈_파일도_깨진_gz로_본다() {
		assertThatThrownBy(() -> TelemetryJsonlParser.parse(KEY, new ByteArrayInputStream(new byte[0])))
				.isInstanceOf(TelemetryFileFormatException.class);
	}

	public static InputStream gzip(String... lines) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
			out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return new ByteArrayInputStream(bytes.toByteArray());
	}

	private static void assertRejected(String line, String reason) {
		assertThatThrownBy(() -> TelemetryJsonlParser.parseLine(line))
				.isInstanceOf(InvalidTelemetryLineException.class)
				.hasMessageContaining(reason);
	}

	private static String microtripPld(String extraFields) {
		return """
				{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"k",\
				"pld":{"tid":354,"clt":1793575800000,%s}}""".formatted(extraFields);
	}

}
