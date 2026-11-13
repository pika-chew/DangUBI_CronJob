package com.pikachew.dangUBI.infra.local;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;
import com.pikachew.dangUBI.infra.swift.SwiftObject;

/** 단위 테스트: 로컬 디렉터리를 Swift처럼 읽는 클라이언트. 디렉터리 처리 자체는 LocalObjectDirectoryTest에서 본다 */
class LocalSwiftClientTest {

	@TempDir
	private Path root;

	private LocalSwiftClient client;

	@BeforeEach
	void setUp() {
		client = new LocalSwiftClient(new LocalStorageProperties(root));
	}

	@Test
	void swift_디렉터리_아래_파일만_Swift_객체로_돌려준다() throws IOException {
		write("swift/firehose/processed/2026/11/08/03/a.jsonl.gz", "abc");
		write("s3/firehose/processed/2026/11/08/03/b.jsonl.gz", "b");

		assertThat(client.listObjects("firehose/processed/2026/11/08/"))
				.containsExactly(new SwiftObject("firehose/processed/2026/11/08/03/a.jsonl.gz", 3));
	}

	@Test
	void 키로_swift_디렉터리의_파일을_연다() throws IOException {
		write("swift/firehose/processed/2026/11/08/03/a.jsonl.gz", "content");

		try (InputStream in = client.download("firehose/processed/2026/11/08/03/a.jsonl.gz")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
		}
	}

	private void write(String relativePath, String content) throws IOException {
		Path file = root.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

}
