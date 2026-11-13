package com.pikachew.dangUBI.infra.swift;

import static com.pikachew.dangUBI.infra.swift.TelemetryJsonlParserTest.MICROTRIP_LINE;
import static com.pikachew.dangUBI.infra.swift.TelemetryJsonlParserTest.gzip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;

/** 단위 테스트: Swift 포트 구현체. Swift 클라이언트는 Mock으로 바꾼다 */
class SwiftTelemetryFileReaderTest {

	private static final LocalDate DAY = LocalDate.parse("2026-11-10");

	private final SwiftClient swiftClient = mock(SwiftClient.class);
	private final SwiftTelemetryFileReader reader = new SwiftTelemetryFileReader(swiftClient);

	@Test
	void 그날_prefix로_조회해_적재_대상만_키_순서로_돌려준다() {
		given(swiftClient.listObjects("firehose/processed/2026/11/10/")).willReturn(List.of(
				new SwiftObject("firehose/processed/2026/11/10/05/b.jsonl.gz", 10),
				new SwiftObject("firehose/processed/2026/11/10/03/a.jsonl.gz", 10),
				new SwiftObject("firehose/processed/2026/11/10/03/readme.txt", 10),
				new SwiftObject("firehose/processed/2026/11/10/03/", 0)));

		List<String> files = reader.listFiles(DAY);

		assertThat(files).containsExactly(
				"firehose/processed/2026/11/10/03/a.jsonl.gz",
				"firehose/processed/2026/11/10/05/b.jsonl.gz");
	}

	@Test
	void 파일을_내려받아_해석하고_스트림을_닫는다() {
		String key = "firehose/processed/2026/11/10/03/a.jsonl.gz";
		AtomicBoolean closed = new AtomicBoolean(false);
		InputStream content = gzip(MICROTRIP_LINE);
		given(swiftClient.download(key)).willReturn(new InputStream() {
			@Override
			public int read() throws IOException {
				return content.read();
			}

			@Override
			public void close() {
				closed.set(true);
			}
		});

		ParsedTelemetryFile parsed = reader.read(key);

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(closed).isTrue();
	}

}
