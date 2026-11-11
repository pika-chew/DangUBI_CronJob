package com.pikachew.dangUBI.batch.ingest.processor;

import static com.pikachew.dangUBI.domain.telemetry.policy.TelemetryRecordResolverTest.MICROTRIP_LINE;
import static com.pikachew.dangUBI.domain.telemetry.policy.TelemetryRecordResolverTest.TRIP_LINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.RawObjectReader;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.domain.telemetry.exception.TelemetryHandler;
import com.pikachew.dangUBI.domain.telemetry.policy.TelemetryRecordResolver;
import org.junit.jupiter.api.Test;

/** 단위 테스트: Swift 키 하나를 레코드 목록으로 바꾸는 Processor. Swift는 메모리 가짜 저장소로 바꾼다. */
class IngestFileProcessorTest {

	private static final String KEY = "firehose/processed/2026/11/10/03/a.jsonl.gz";

	private final FakeRawObjectReader store = new FakeRawObjectReader();
	private final IngestFileProcessor processor = new IngestFileProcessor(store, new TelemetryRecordResolver());

	@Test
	void trip과_microtrip을_나눠_담는다() {
		store.put(KEY, gzip(TRIP_LINE, MICROTRIP_LINE,
				MICROTRIP_LINE.replace("example-microtrip-1", "example-microtrip-2")));

		ParsedTelemetryFile parsed = processor.process(KEY);

		assertThat(parsed.objectKey()).isEqualTo(KEY);
		assertThat(parsed.trips()).hasSize(1);
		assertThat(parsed.microtrips()).hasSize(2);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void 해석하지_못한_줄은_건너뛰고_센다() {
		store.put(KEY, gzip(MICROTRIP_LINE, "{broken", TRIP_LINE.replace("\"ty\":3", "\"ty\":9")));

		ParsedTelemetryFile parsed = processor.process(KEY);

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isEqualTo(2);
	}

	@Test
	void 빈_줄은_세지_않는다() {
		store.put(KEY, gzip("", MICROTRIP_LINE, "   ", ""));

		ParsedTelemetryFile parsed = processor.process(KEY);

		assertThat(parsed.microtrips()).hasSize(1);
		assertThat(parsed.skippedLineCount()).isZero();
	}

	@Test
	void 읽은_뒤에는_스트림을_닫는다() {
		store.put(KEY, gzip(MICROTRIP_LINE));

		processor.process(KEY);

		assertThat(store.closedKeys).containsExactly(KEY);
	}

	@Test
	void gz가_깨졌으면_파일을_읽지_못했다는_예외를_던진다() {
		store.put(KEY, "plain text".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> processor.process(KEY))
				.isInstanceOf(TelemetryHandler.class)
				.satisfies(e -> assertThat(((TelemetryHandler) e).getErrorCode())
						.isEqualTo(ErrorStatus.TELEMETRY_FILE_UNREADABLE))
				.hasMessageContaining(KEY);
		assertThat(store.closedKeys).containsExactly(KEY);
	}

	@Test
	void 빈_파일도_깨진_gz로_본다() {
		store.put(KEY, new byte[0]);

		assertThatThrownBy(() -> processor.process(KEY)).isInstanceOf(TelemetryHandler.class);
	}

	private static byte[] gzip(String... lines) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
			out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	private static class FakeRawObjectReader implements RawObjectReader {

		private final Map<String, byte[]> objects = new HashMap<>();
		private final List<String> closedKeys = new java.util.ArrayList<>();

		void put(String key, byte[] content) {
			objects.put(key, content);
		}

		@Override
		public List<String> listKeys(String prefix) {
			return objects.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
		}

		@Override
		public InputStream open(String key) {
			return new ByteArrayInputStream(objects.get(key)) {
				@Override
				public void close() throws IOException {
					closedKeys.add(key);
					super.close();
				}
			};
		}

	}

}
