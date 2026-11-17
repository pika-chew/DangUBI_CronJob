package com.pikachew.dangUBI.batch.ingest.processor;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.ingest.dto.ParsedTelemetryFile;
import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;
import com.pikachew.dangUBI.common.exception.ErrorStatus;
import com.pikachew.dangUBI.common.exception.GeneralException;
import com.pikachew.dangUBI.infra.swift.TelemetryFileFormatException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ingestJob의 Processor: 수집 파일 키 하나를 trip·microtrip 목록으로 바꾼다. 저장은 하지 않는다.
 * 파일 형식(gz·JSONL) 해석은 포트 구현체(infra)가 맡는다.
 *
 * 파일 자체를 읽을 수 없으면 ErrorStatus.INGEST_FILE_UNREADABLE로 바꿔 Step을 실패시킨다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestFileProcessor implements ItemProcessor<String, ParsedTelemetryFile> {

	private final TelemetryFileReader telemetryFileReader;

	@Override
	public ParsedTelemetryFile process(String objectKey) {
		try {
			return telemetryFileReader.read(objectKey);
		} catch (TelemetryFileFormatException e) {
			log.error("수집 파일을 읽지 못했습니다: {}", objectKey, e);
			throw new GeneralException(ErrorStatus.INGEST_FILE_UNREADABLE);
		}
	}

}
