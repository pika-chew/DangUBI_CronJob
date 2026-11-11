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
import com.pikachew.dangUBI.batch.ingest.port.RawObjectReader;
import com.pikachew.dangUBI.domain.telemetry.exception.TelemetryHandler;
import com.pikachew.dangUBI.domain.telemetry.model.MicrotripRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TelemetryRecord;
import com.pikachew.dangUBI.domain.telemetry.model.TripRecord;
import com.pikachew.dangUBI.domain.telemetry.policy.TelemetryRecordResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * ingestJob의 Processor: Swift 객체 키 하나를 받아 gz JSONL을 레코드 목록으로 바꾼다. 저장은 하지 않는다.
 *
 * - 줄 해석 규칙은 {@link TelemetryRecordResolver}(domain)에 있다.
 * - 빈 줄은 건너뛰고 세지 않는다. 버려진 줄은 경고를 남기고 skippedLineCount로 센다.
 * - gz 자체가 깨졌으면 {@link TelemetryHandler#fileUnreadable}을 던져 Step을 실패시킨다.
 *
 * TODO: 파일이 커서 메모리에 다 올리기 어려우면 레코드를 스트리밍으로 넘기는 방식으로 바꾼다.
 */
@Slf4j
@Component
public class IngestFileProcessor implements ItemProcessor<String, ParsedTelemetryFile> {

	private final RawObjectReader rawObjectReader;
	private final TelemetryRecordResolver recordResolver;

	public IngestFileProcessor(RawObjectReader rawObjectReader, TelemetryRecordResolver recordResolver) {
		this.rawObjectReader = rawObjectReader;
		this.recordResolver = recordResolver;
	}

	@Override
	public ParsedTelemetryFile process(String objectKey) {
		try (InputStream gzipped = rawObjectReader.open(objectKey)) {
			return parse(objectKey, gzipped);
		} catch (IOException e) {
			throw TelemetryHandler.fileUnreadable(objectKey, e);
		}
	}

	private ParsedTelemetryFile parse(String objectKey, InputStream gzipped) throws IOException {
		List<TripRecord> trips = new ArrayList<>();
		List<MicrotripRecord> microtrips = new ArrayList<>();
		int skipped = 0;

		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new GZIPInputStream(gzipped), StandardCharsets.UTF_8));
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

		return new ParsedTelemetryFile(objectKey, trips, microtrips, skipped);
	}

}
