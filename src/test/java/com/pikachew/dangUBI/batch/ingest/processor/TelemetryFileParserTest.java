package com.pikachew.dangUBI.batch.ingest.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import org.junit.jupiter.api.Test;

/** 단위 테스트: gz JSONL 파일 하나 해석. */
class TelemetryFileParserTest {

	private static final String KEY = "firehose/processed/vehicleId=vehicle-001/tid=354/ty=4/2026/11/10/03/a.jsonl.gz";

	private final TelemetryFileParser parser = new TelemetryFileParser(new TelemetryLineParser());

	@Test
	void trip과_microtrip을_나눠_담는다() {
		ParsedTelemetryFile parsed = parser.parse(KEY, gzip(
				TelemetryLineParserTest.TRIP_LINE,
				TelemetryLineParserTest.MICROTRIP_LINE,
				TelemetryLineParserTest.MICROTRIP_LINE.replace("example-microtrip-1", "example-microtrip-2")));

		assertThat(parsed.objectKey()).isEqualTo(KEY);
		assertThat(parsed.trips()).hasSize(1);
		assertThat(parsed.microtrips()).hasSize(2);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void 해석하지_못한_줄은_건너뛰고_센다() {
		ParsedTelemetryFile parsed = parser.parse(KEY, gzip(
				TelemetryLineParserTest.MICROTRIP_LINE,
				"{broken",
				TelemetryLineParserTest.TRIP_LINE.replace("\"ty\":3", "\"ty\":9")));

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isEqualTo(2);
	}

	@Test
	void 빈_줄은_세지_않는다() {
		ParsedTelemetryFile parsed = parser.parse(KEY, gzip("", TelemetryLineParserTest.MICROTRIP_LINE, "   ", ""));

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void gz가_깨졌으면_예외를_던진다() {
		InputStream notGzip = new ByteArrayInputStream("plain text".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> parser.parse(KEY, notGzip))
				.isInstanceOf(UncheckedIOException.class)
				.hasMessageContaining(KEY);
	}

	@Test
	void 빈_파일도_깨진_gz로_본다() {
		assertThatThrownBy(() -> parser.parse(KEY, new ByteArrayInputStream(new byte[0])))
				.isInstanceOf(UncheckedIOException.class);
	}

	private static InputStream gzip(String... lines) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
			out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return new ByteArrayInputStream(bytes.toByteArray());
	}

}
