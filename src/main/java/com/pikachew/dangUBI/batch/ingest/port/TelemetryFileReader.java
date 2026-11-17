package com.pikachew.dangUBI.batch.ingest.port;

import java.time.LocalDate;
import java.util.List;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;

/**
 * 원본 저장소(Swift)의 수집 파일을 읽어오는 포트. Job이 저장 경로나 파일 형식(gz·JSONL)에 의존하지 않도록 분리한다.
 * 구현체: infra/swift/SwiftTelemetryFileReader
 */
public interface TelemetryFileReader {

	/** 그날(UTC) 적재할 파일 키. 키 순서로 정렬해 돌려준다 */
	List<String> listFiles(LocalDate date);

	/**
	 * 파일 하나를 읽어 trip·microtrip으로 나눈다. 해석하지 못한 줄은 건너뛰고 수만 센다.
	 *
	 * @throws com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException 파일 자체를 읽을 수 없을 때
	 */
	ParsedTelemetryFile read(String key);

}
