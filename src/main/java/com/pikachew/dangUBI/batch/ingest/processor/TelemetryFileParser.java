package com.pikachew.dangUBI.batch.ingest.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import com.pikachew.dangUBI.batch.ingest.dto.MicrotripRecord;
import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.dto.TelemetryRecord;
import com.pikachew.dangUBI.batch.ingest.dto.TripRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * gz로 압축된 JSONL 파일 하나를 해석한다.
 *
 * - 빈 줄은 건너뛰고 세지 않는다.
 * - 해석하지 못한 줄은 경고를 남기고 skippedLineCount로 센다.
 * - gz 자체가 깨졌으면(읽기 실패) {@link UncheckedIOException}을 던진다. 호출한 쪽에서 파일을 실패로 기록한다.
 *
 * TODO: 파일이 커서 메모리에 다 올리기 어려우면 레코드를 스트리밍으로 넘기는 방식으로 바꾼다.
 */
@Slf4j
@Component
public class TelemetryFileParser {

	private final TelemetryLineParser lineParser;

	public TelemetryFileParser(TelemetryLineParser lineParser) {
		this.lineParser = lineParser;
	}

	public ParsedTelemetryFile parse(String objectKey, InputStream gzipped) {
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
					TelemetryRecord record = lineParser.parse(line);
					if (record instanceof TripRecord trip) {
						trips.add(trip);
					} else if (record instanceof MicrotripRecord microtrip) {
						microtrips.add(microtrip);
					}
				} catch (InvalidTelemetryLineException e) {
					skipped++;
					log.warn("줄을 건너뜁니다: {}:{} ({})", objectKey, lineNumber, e.getMessage());
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("gz 파일을 읽지 못했습니다: " + objectKey, e);
		}

		return new ParsedTelemetryFile(objectKey, trips, microtrips, skipped);
	}

}
