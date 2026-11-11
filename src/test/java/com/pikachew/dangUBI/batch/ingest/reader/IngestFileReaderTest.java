package com.pikachew.dangUBI.batch.ingest.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.common.consts.IngestStaticValues;

/** 단위 테스트: 하루치 파일 키를 넘기고, 마지막 키를 남겨 이어 읽는 Reader */
class IngestFileReaderTest {

	private static final String A = "firehose/processed/2026/11/10/03/a.jsonl.gz";
	private static final String B = "firehose/processed/2026/11/10/04/b.jsonl.gz";
	private static final String C = "firehose/processed/2026/11/10/05/c.jsonl.gz";

	private final TelemetryFileReader telemetryFileReader = mock(TelemetryFileReader.class);
	private final IngestFileReader reader = new IngestFileReader(telemetryFileReader, "2026-11-10");

	@Test
	void targetDate의_파일_키를_차례로_넘기고_끝나면_null이다() {
		given(telemetryFileReader.listFiles(LocalDate.parse("2026-11-10"))).willReturn(List.of(A, B));

		reader.open(new ExecutionContext());

		assertThat(reader.read()).isEqualTo(A);
		assertThat(reader.read()).isEqualTo(B);
		assertThat(reader.read()).isNull();
	}

	@Test
	void 마지막으로_넘긴_키를_실행_기록에_남긴다() {
		given(telemetryFileReader.listFiles(LocalDate.parse("2026-11-10"))).willReturn(List.of(A, B));
		ExecutionContext context = new ExecutionContext();
		reader.open(context);

		reader.read();
		reader.update(context);

		assertThat(context.getString(IngestStaticValues.INGEST_READER_LAST_KEY)).isEqualTo(A);
	}

	@Test
	void 실행_기록에_키가_있으면_그_다음_키부터_읽는다() {
		given(telemetryFileReader.listFiles(LocalDate.parse("2026-11-10"))).willReturn(List.of(A, B, C));
		ExecutionContext context = new ExecutionContext();
		context.putString(IngestStaticValues.INGEST_READER_LAST_KEY, A);

		reader.open(context);

		assertThat(reader.read()).isEqualTo(B);
		assertThat(reader.read()).isEqualTo(C);
		assertThat(reader.read()).isNull();
	}

	@Test
	void 아무것도_읽지_않았으면_실행_기록을_남기지_않는다() {
		given(telemetryFileReader.listFiles(LocalDate.parse("2026-11-10"))).willReturn(List.of());
		ExecutionContext context = new ExecutionContext();
		reader.open(context);

		reader.update(context);

		assertThat(context.containsKey(IngestStaticValues.INGEST_READER_LAST_KEY)).isFalse();
	}

}
