package com.pikachew.dangUBI.infra.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 단위 테스트: 로컬 디렉터리를 버킷처럼 다루는 클래스. 임시 디렉터리를 루트로 쓴다 */
class LocalObjectDirectoryTest {

	@TempDir
	private Path root;

	private LocalObjectDirectory directory;

	@BeforeEach
	void setUp() {
		directory = new LocalObjectDirectory(root);
	}

	@Test
	void prefix_아래_파일을_슬래시로_이은_키와_크기로_돌려준다() throws IOException {
		write("firehose/processed/2026/11/08/03/a.jsonl.gz", "abc");
		write("firehose/processed/2026/11/08/14/b.jsonl.gz", "hello");
		write("firehose/processed/2026/11/09/00/c.jsonl.gz", "x");

		List<LocalObject> objects = directory.list("firehose/processed/2026/11/08/");

		assertThat(objects).containsExactlyInAnyOrder(
				new LocalObject("firehose/processed/2026/11/08/03/a.jsonl.gz", 3),
				new LocalObject("firehose/processed/2026/11/08/14/b.jsonl.gz", 5));
	}

	@Test
	void prefix가_디렉터리_중간에서_끝나도_이름_앞부분으로_거른다() throws IOException {
		write("firehose/processed/2026/11/08/03/a.jsonl.gz", "a");
		write("firehose/processed/2026/11/08/03/b.jsonl.gz", "b");

		List<LocalObject> objects = directory.list("firehose/processed/2026/11/08/03/a");

		assertThat(objects).extracting(LocalObject::key)
				.containsExactly("firehose/processed/2026/11/08/03/a.jsonl.gz");
	}

	@Test
	void prefix_디렉터리가_없으면_빈_목록을_돌려준다() {
		assertThat(directory.list("firehose/processed/2026/11/08/")).isEmpty();
	}

	@Test
	void 키로_파일_내용을_연다() throws IOException {
		write("firehose/processed/2026/11/08/03/a.jsonl.gz", "content");

		try (InputStream in = directory.open("firehose/processed/2026/11/08/03/a.jsonl.gz")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
		}
	}

	@Test
	void 없는_파일을_열면_LocalStorageException을_던진다() {
		assertThatThrownBy(() -> directory.open("firehose/processed/2026/11/08/03/none.jsonl.gz"))
				.isInstanceOf(LocalStorageException.class);
	}

	@Test
	void 루트_밖을_가리키는_키는_거부한다() throws IOException {
		Path inner = root.resolve("bucket");
		Files.createDirectories(inner);
		write("secret.txt", "secret");
		LocalObjectDirectory bucket = new LocalObjectDirectory(inner);

		assertThatThrownBy(() -> bucket.open("../secret.txt"))
				.isInstanceOf(LocalStorageException.class);
		assertThatThrownBy(() -> bucket.list("../"))
				.isInstanceOf(LocalStorageException.class);
	}

	private void write(String relativePath, String content) throws IOException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

}
