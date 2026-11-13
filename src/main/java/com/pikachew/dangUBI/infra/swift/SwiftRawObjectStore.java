package com.pikachew.dangUBI.infra.swift;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;
import com.pikachew.dangUBI.infra.storage.TelemetryObjectKeys;

import lombok.RequiredArgsConstructor;

/**
 * Swift에 옮겨진 수집 파일 목록을 돌려주는 {@link RawObjectStore} 구현체.
 * Swift 접근은 {@link SwiftClient}, 저장 경로 규칙은 {@link TelemetryObjectKeys}를 ingestJob과 같이 쓴다 (docs/ingest-spec.md 2장).
 */
@Component
@RequiredArgsConstructor
public class SwiftRawObjectStore implements RawObjectStore {

	private final SwiftClient swiftClient;

	@Override
	public List<ObjectRef> listObjects(LocalDate targetDate) {
		return swiftClient.listObjects(TelemetryObjectKeys.dayPrefix(targetDate)).stream()
				.filter(object -> TelemetryObjectKeys.isTelemetryFile(object.name(), targetDate))
				.map(object -> new ObjectRef(object.name(), object.bytes()))
				.toList();
	}

}
