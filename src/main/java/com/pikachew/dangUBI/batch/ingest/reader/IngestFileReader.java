package com.pikachew.dangUBI.batch.ingest.reader;

import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_READER_LAST_KEY;
import static com.pikachew.dangUBI.common.consts.IngestStaticValues.INGEST_TARGET_DATE_EXPRESSION;

import java.time.LocalDate;
import java.util.Iterator;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.ingest.port.TelemetryFileReader;

import lombok.extern.slf4j.Slf4j;

/**
 * ingestJob의 Reader: targetDate(UTC) 하루치 수집 파일 키를 키 순서로 하나씩 넘긴다.
 *
 * 몇 번째까지 읽었는지가 아니라 마지막으로 넘긴 키를 실행 기록에 남긴다. 실패한 실행을 다시 돌리면 그 키 다음부터 읽는다.
 * 그사이 앞쪽에 늦게 들어온 파일은 이어 읽기에서 빠지므로 rerun으로 다시 적재한다 (docs/ingest-spec.md 6장).
 */
@Slf4j
@Component
@StepScope
public class IngestFileReader implements ItemStreamReader<String> {

	private final TelemetryFileReader telemetryFileReader;
	private final LocalDate targetDate;

	private Iterator<String> keys;
	private String lastKey;

	public IngestFileReader(TelemetryFileReader telemetryFileReader,
			@Value(INGEST_TARGET_DATE_EXPRESSION) String targetDate) {
		this.telemetryFileReader = telemetryFileReader;
		this.targetDate = LocalDate.parse(targetDate);
	}

	@Override
	public void open(ExecutionContext executionContext) {
		lastKey = executionContext.containsKey(INGEST_READER_LAST_KEY) ? executionContext.getString(INGEST_READER_LAST_KEY) : null;
		keys = telemetryFileReader.listFiles(targetDate).stream()
				.filter(key -> lastKey == null || key.compareTo(lastKey) > 0)
				.iterator();
		log.info("ingest {}: 읽기 시작 (이어 읽기 기준 키: {})", targetDate, lastKey);
	}

	@Override
	public String read() {
		if (!keys.hasNext()) {
			return null;
		}
		lastKey = keys.next();
		return lastKey;
	}

	@Override
	public void update(ExecutionContext executionContext) {
		if (lastKey != null) {
			executionContext.putString(INGEST_READER_LAST_KEY, lastKey);
		}
	}

}
