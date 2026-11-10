package com.pikachew.dangUBI.batch.ingest.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.domain.telemetry.exception.TelemetryHandler;
import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TelemetryRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import com.pikachew.dangUBI.domain.telemetry.policy.TelemetryRecordResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ingestJob의 Processor: gz로 압축된 JSONL 파일 하나를 레코드 목록으로 바꾼다. 저장은 하지 않는다.
 *
 * - 줄 해석 규칙은 {@link TelemetryRecordResolver}(domain)에 있다.
 * - 빈 줄은 건너뛰고 세지 않는다. 버려진 줄은 경고를 남기고 skippedLineCount로 센다.
 * - gz 자체가 깨졌으면 {@link TelemetryHandler#fileUnreadable}을 던진다. Step에서 그 파일을 실패로 기록한다.
 *
 * TODO(단위 3): Swift 포트가 생기면 ItemProcessor<파일 참조, ParsedTelemetryFile>로 바꾸고 파일 열기를 포트에 맡긴다.
 * TODO: 파일이 커서 메모리에 다 올리기 어려우면 레코드를 스트리밍으로 넘기는 방식으로 바꾼다.
 */
@Slf4j
@Component
public class IngestFileProcessor {

	private final TelemetryRecordResolver recordResolver;

	public IngestFileProcessor(TelemetryRecordResolver recordResolver) {
		this.recordResolver = recordResolver;
	}

	public ParsedTelemetryFile process(String objectKey, InputStream gzipped) {
		List<TripRecord> trips = new ArrayList<>();
		List<MicrotripRecord> microtrips = new ArrayList<>();
		int skipped = 0;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(gzipped), StandardCharsets.UTF_8))) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				try {
					TelemetryRecord record = recordResolver.resolve(line);
					if (record instanceof TripRecord trip) {
						trips.add(trip);
					} else if (record instanceof MicrotripRecord microtrip) {
						microtrips.add(microtrip);
					}
				} catch (TelemetryHandler e) {
					skipped++;
					log.warn("줄을 건너뜁니다: {}:{} ({})", objectKey, lineNumber, e.getMessage());
				}
			}
		} catch (IOException e) {
			throw TelemetryHandler.fileUnreadable(objectKey, e);
		}

		return new ParsedTelemetryFile(objectKey, trips, microtrips, skipped);
	}

}
