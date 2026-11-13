package com.pikachew.dangUBI.batch.ingest.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException;

/** 단위 테스트: 수집 파일 키 하나를 처리하는 Processor. 포트는 Mock으로 바꾼다 */
class IngestFileProcessorTest {

	private static final String KEY = "firehose/processed/2026/11/10/03/a.jsonl.gz";

	private final TelemetryFileReader telemetryFileReader = mock(TelemetryFileReader.class);
	private final IngestFileProcessor processor = new IngestFileProcessor(telemetryFileReader);

	@Test
	void 포트가_읽은_결과를_그대로_넘긴다() {
		ParsedTelemetryFile parsed = new ParsedTelemetryFile(KEY, List.of(), List.of(), 1);
		given(telemetryFileReader.read(KEY)).willReturn(parsed);

		assertThat(processor.process(KEY)).isSameAs(parsed);
	}

	@Test
	void 파일을_읽을_수_없으면_INGEST_FILE_UNREADABLE로_실패한다() {
		given(telemetryFileReader.read(KEY))
				.willThrow(new TelemetryFileFormatException("gz 파일을 읽지 못했습니다: " + KEY, new IOException("bad")));

		assertThatThrownBy(() -> processor.process(KEY))
				.isInstanceOf(GeneralException.class)
				.satisfies(e -> assertThat(((GeneralException) e).getCode()).isEqualTo(ErrorStatus.INGEST_FILE_UNREADABLE))
				.hasMessage("수집 파일을 읽지 못했습니다.");
	}

}
