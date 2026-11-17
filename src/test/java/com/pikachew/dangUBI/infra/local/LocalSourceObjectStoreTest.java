package com.pikachew.dangUBI.infra.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;

/** 단위 테스트: 로컬 디렉터리를 S3 원본 버킷처럼 읽는 구현체 */
class LocalSourceObjectStoreTest {

	@TempDir
	private Path root;

	private LocalSourceObjectStore store;

	@BeforeEach
	void setUp() {
		store = new LocalSourceObjectStore(new LocalStorageProperties(root));
	}

	@Test
	void s3_디렉터리에서_그날_수집_파일만_이름과_크기로_돌려준다() throws IOException {
		write("s3/firehose/processed/2026/11/08/03/a.jsonl.gz", "abc");
		write("s3/firehose/processed/2026/11/08/03/readme.txt", "x");
		write("s3/firehose/processed/2026/11/08/03/nested/b.jsonl.gz", "x");
		write("s3/firehose/processed/2026/11/09/00/c.jsonl.gz", "x");
		write("swift/firehose/processed/2026/11/08/04/d.jsonl.gz", "x");

		assertThat(store.listObjects(LocalDate.parse("2026-11-08")))
				.containsExactly(new ObjectRef("firehose/processed/2026/11/08/03/a.jsonl.gz", 3));
	}

	@Test
	void 그날_디렉터리가_없으면_빈_목록을_돌려준다() {
		assertThat(store.listObjects(LocalDate.parse("2026-11-08"))).isEmpty();
	}

	private void write(String relativePath, String content) throws IOException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

}
