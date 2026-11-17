package com.pikachew.dangUBI.infra.swift;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;

/** 단위 테스트: reconcile용 Swift 목록 구현체. Swift 클라이언트는 Mock으로 바꾼다 */
class SwiftRawObjectStoreTest {

	private final SwiftClient swiftClient = mock(SwiftClient.class);
	private final SwiftRawObjectStore store = new SwiftRawObjectStore(swiftClient);

	@Test
	void 그날_prefix로_조회해_수집_파일만_이름과_크기로_돌려준다() {
		given(swiftClient.listObjects("firehose/processed/2026/11/08/")).willReturn(List.of(
				new SwiftObject("firehose/processed/2026/11/08/03/a.jsonl.gz", 120),
				new SwiftObject("firehose/processed/2026/11/08/03/readme.txt", 5),
				new SwiftObject("firehose/processed/2026/11/08/03/", 0)));

		List<ObjectRef> objects = store.listObjects(LocalDate.parse("2026-11-08"));

		assertThat(objects).containsExactly(new ObjectRef("firehose/processed/2026/11/08/03/a.jsonl.gz", 120));
	}

}
