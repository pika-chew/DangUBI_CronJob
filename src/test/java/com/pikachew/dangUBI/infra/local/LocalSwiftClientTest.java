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

import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;
import com.pikachew.dangUBI.infra.swift.SwiftObject;

/** 단위 테스트: 로컬 디렉터리를 Swift처럼 읽는 클라이언트. 임시 디렉터리를 루트로 쓴다 */
class LocalSwiftClientTest {

	@TempDir
	private Path root;

	private LocalSwiftClient client;

	@BeforeEach
	void setUp() {
		client = new LocalSwiftClient(new LocalStorageProperties(root));
	}

	@Test
	void prefix_아래_파일을_슬래시로_이은_키와_크기로_돌려준다() throws IOException {
		write("swift/firehose/processed/2026/11/08/03/a.jsonl.gz", "abc");
		write("swift/firehose/processed/2026/11/08/14/b.jsonl.gz", "hello");
		write("swift/firehose/processed/2026/11/09/00/c.jsonl.gz", "x");

		List<SwiftObject> objects = client.listObjects("firehose/processed/2026/11/08/");

		assertThat(objects).containsExactlyInAnyOrder(
				new SwiftObject("firehose/processed/2026/11/08/03/a.jsonl.gz", 3),
				new SwiftObject("firehose/processed/2026/11/08/14/b.jsonl.gz", 5));
	}

	@Test
	void prefix가_디렉터리_중간에서_끝나도_이름_앞부분으로_거른다() throws IOException {
		write("swift/firehose/processed/2026/11/08/03/a.jsonl.gz", "a");
		write("swift/firehose/processed/2026/11/08/03/b.jsonl.gz", "b");

		List<SwiftObject> objects = client.listObjects("firehose/processed/2026/11/08/03/a");

		assertThat(objects).extracting(SwiftObject::name)
				.containsExactly("firehose/processed/2026/11/08/03/a.jsonl.gz");
	}

	@Test
	void swift_디렉터리_밖의_파일은_목록에_넣지_않는다() throws IOException {
		write("s3/firehose/processed/2026/11/08/03/a.jsonl.gz", "a");

		assertThat(client.listObjects("firehose/processed/2026/11/08/")).isEmpty();
	}

	@Test
	void prefix_디렉터리가_없으면_빈_목록을_돌려준다() {
		assertThat(client.listObjects("firehose/processed/2026/11/08/")).isEmpty();
	}

	@Test
	void 키로_파일_내용을_연다() throws IOException {
		write("swift/firehose/processed/2026/11/08/03/a.jsonl.gz", "content");

		try (InputStream in = client.download("firehose/processed/2026/11/08/03/a.jsonl.gz")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
		}
	}

	@Test
	void 없는_파일을_열면_LocalStorageException을_던진다() {
		assertThatThrownBy(() -> client.download("firehose/processed/2026/11/08/03/none.jsonl.gz"))
				.isInstanceOf(LocalStorageException.class);
	}

	@Test
	void 루트_밖을_가리키는_키는_거부한다() throws IOException {
		write("secret.txt", "secret");

		assertThatThrownBy(() -> client.download("../secret.txt"))
				.isInstanceOf(LocalStorageException.class);
	}

	private void write(String relativePath, String content) throws IOException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

}
