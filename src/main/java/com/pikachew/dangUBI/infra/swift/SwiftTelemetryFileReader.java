package com.pikachew.dangUBI.infra.swift;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;

import lombok.RequiredArgsConstructor;

/**
 * Swift에 저장된 수집 파일을 읽는 {@link TelemetryFileReader} 구현체.
 * 저장 경로는 {@link TelemetryObjectKeys}, 파일 형식은 {@link TelemetryJsonlParser}가 안다.
 */
@Component
@RequiredArgsConstructor
public class SwiftTelemetryFileReader implements TelemetryFileReader {

	private final SwiftClient swiftClient;

	@Override
	public List<String> listFiles(LocalDate date) {
		return swiftClient.listObjects(TelemetryObjectKeys.dayPrefix(date)).stream()
				.map(SwiftObject::name)
				.filter(key -> TelemetryObjectKeys.isTelemetryFile(key, date))
				.sorted()
				.toList();
	}

	@Override
	public ParsedTelemetryFile read(String key) {
		try (InputStream gzipped = swiftClient.download(key)) {
			return TelemetryJsonlParser.parse(key, gzipped);
		} catch (IOException e) {
			throw new TelemetryFileFormatException("수집 파일을 닫지 못했습니다: " + key, e);
		}
	}

}
