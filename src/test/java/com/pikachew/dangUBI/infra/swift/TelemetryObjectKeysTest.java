package com.pikachew.dangUBI.infra.swift;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 단위 테스트: 수집 파일 저장 경로 규칙 */
class TelemetryObjectKeysTest {

	private static final LocalDate DAY = LocalDate.parse("2026-11-10");

	@Test
	void 하루치_prefix는_날짜_경로다() {
		assertThat(TelemetryObjectKeys.dayPrefix(DAY)).isEqualTo("firehose/processed/2026/11/10/");
		assertThat(TelemetryObjectKeys.dayPrefix(LocalDate.parse("2026-01-05"))).isEqualTo("firehose/processed/2026/01/05/");
	}

	@Test
	void 그날_시각_폴더_아래_gz_파일만_적재_대상이다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/00/a.jsonl.gz", DAY)).isTrue();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/23/ubi-1-2026-11-10-23-59-59-uuid.gz", DAY)).isTrue();
	}

	@Test
	void 다른_날짜의_파일은_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/11/00/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void 시각_폴더가_없거나_틀리면_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/a.jsonl.gz", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/24/a.jsonl.gz", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/03/sub/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void gz가_아니면_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/03/a.jsonl", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/03/", DAY)).isFalse();
	}

	@Test
	void 에러_출력_경로는_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/errors/processing-failed/2026/11/10/03/a.gz", DAY)).isFalse();
	}

}
