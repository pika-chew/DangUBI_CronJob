package com.pikachew.dangUBI.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** 단위 테스트: 수집 파일 저장 경로 규칙 */
class TelemetryObjectKeysTest {

	private static final LocalDate DAY = LocalDate.parse("2026-11-10");

	@Test
	void 하루치_prefix는_날짜_경로다() {
		assertThat(TelemetryObjectKeys.dayPrefix(DAY)).isEqualTo("firehose/processed/year=2026/month=11/day=10/");
		assertThat(TelemetryObjectKeys.dayPrefix(LocalDate.parse("2026-01-05"))).isEqualTo("firehose/processed/year=2026/month=01/day=05/");
	}

	@Test
	void 그날_시각_폴더_아래_gz_파일만_적재_대상이다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=00/a.jsonl.gz", DAY)).isTrue();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=23/ubi-1-2026-11-10-23-59-59-uuid.gz", DAY)).isTrue();
	}

	@Test
	void Firehose가_실제로_쓰는_키를_적재_대상으로_본다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile(
				"firehose/processed/year=2026/month=11/day=16/hour=05/ubi-dev-processed-firehose-2-2026-11-16-05-49-06-2346ca95-7f60-4d13-a9c3-ab00dada2e25.jsonl.gz",
				LocalDate.parse("2026-11-16"))).isTrue();
	}

	@Test
	void 파티션_이름이_없는_예전_경로는_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/2026/11/10/03/a.jsonl.gz", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/03/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void 날짜_파티션_밖의_파일은_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/collector-smoke-01/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void 다른_날짜의_파일은_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=11/hour=00/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void 시각_폴더가_없거나_틀리면_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/a.jsonl.gz", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=24/a.jsonl.gz", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=03/sub/a.jsonl.gz", DAY)).isFalse();
	}

	@Test
	void gz가_아니면_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=03/a.jsonl", DAY)).isFalse();
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/processed/year=2026/month=11/day=10/hour=03/", DAY)).isFalse();
	}

	@Test
	void 에러_출력_경로는_대상이_아니다() {
		assertThat(TelemetryObjectKeys.isTelemetryFile("firehose/errors/processing-failed/2026/11/10/03/a.gz", DAY)).isFalse();
	}

}
