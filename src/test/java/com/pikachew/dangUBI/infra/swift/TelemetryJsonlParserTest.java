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

	public static final String MICROTRIP_LINE = """
			{"vehicleId":"vehicle-001","ts":1793575800100,"ty":4,"schemaVersion":1,"idempotencyKey":"example-microtrip-1",\
			"pld":{"tid":354,"clt":1793575800000,"sp":70,"em":"28"}}""";

	private static final String KEY = "firehose/processed/2026/11/10/03/a.jsonl.gz";

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
	void 문서상_필수여도_실제로_빠진_필드는_null이다() {
		TripSnapshot trip = (TripSnapshot) TelemetryJsonlParser.parseLine(TRIP_LINE);

		assertThat(trip.totalDistance()).isNull();
		assertThat(trip.fuelConsumption()).isNull();
		assertThat(trip.startLat()).isNull();
	}

	@Test
	void ty4_줄을_MicrotripSnapshot으로_해석한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(MICROTRIP_LINE);

		assertThat(microtrip.tid()).isEqualTo(354);
		assertThat(microtrip.collectedAt()).isEqualTo(Instant.ofEpochMilli(1793575800000L));
		assertThat(microtrip.speed()).isEqualTo(70);
		assertThat(microtrip.lat()).isNull();
	}

	@Test
	void em은_16진으로_해석한다_28은_급유턴과_급가속이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(MICROTRIP_LINE);

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
		assertThat(microtrip.eventMask() & (1 << 3)).isNotZero();
		assertThat(microtrip.eventMask() & (1 << 5)).isNotZero();
	}

	@Test
	void em이_숫자로_와도_16진으로_해석한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":28"));

		assertThat(microtrip.eventMask()).isEqualTo(0x28);
	}

	@Test
	void em이_16진이_아니면_그_필드만_null이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"em\":\"XZ\",\"sp\":70"));

		assertThat(microtrip.eventMask()).isNull();
		assertThat(microtrip.speed()).isEqualTo(70);
	}

	@Test
	void xyz는_쉼표로_나눠_세_값으로_해석한다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"xyz\":\"12,-3, 7\""));

		assertThat(microtrip.accX()).isEqualTo(12);
		assertThat(microtrip.accY()).isEqualTo(-3);
		assertThat(microtrip.gyroY()).isEqualTo(7);
	}

	@Test
	void xyz_형식이_틀리면_세_값이_모두_null이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"xyz\":\"12,abc,7\""));

		assertThat(microtrip.accX()).isNull();
		assertThat(microtrip.accY()).isNull();
		assertThat(microtrip.gyroY()).isNull();
	}

	@Test
	void 숫자_문자열도_숫자로_받는다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"sp\":\"72\""));

		assertThat(microtrip.speed()).isEqualTo(72);
	}

	@Test
	void 선택_필드_형식이_틀리면_그_필드만_null이다() {
		MicrotripSnapshot microtrip = (MicrotripSnapshot) TelemetryJsonlParser.parseLine(microtripPld("\"sp\":\"fast\",\"rpm\":2000"));

		assertThat(microtrip.speed()).isNull();
		assertThat(microtrip.rpm()).isEqualTo(2000);
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
