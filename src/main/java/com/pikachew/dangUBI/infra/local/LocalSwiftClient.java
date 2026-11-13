package com.pikachew.dangUBI.infra.local;

import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_KEY_DELIMITER;
import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_SWIFT_DIR;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.LOCAL_PROFILE;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;
import com.pikachew.dangUBI.infra.swift.SwiftClient;
import com.pikachew.dangUBI.infra.swift.SwiftObject;

/**
 * local 프로필에서 Swift 대신 로컬 디렉터리({root}/swift)를 읽는 {@link SwiftClient} 구현체.
 * 디렉터리 아래 상대 경로를 '/'로 이은 값이 객체 키다. 예: {root}/swift/firehose/processed/2026/11/08/03/a.jsonl.gz
 */
@Component
@Profile(LOCAL_PROFILE)
public class LocalSwiftClient implements SwiftClient {

	private final Path root;

	public LocalSwiftClient(LocalStorageProperties properties) {
		this.root = properties.root().resolve(LOCAL_STORAGE_SWIFT_DIR).toAbsolutePath().normalize();
	}

	@Override
	public List<SwiftObject> listObjects(String prefix) {
		Path start = resolve(prefix.substring(0, prefix.lastIndexOf(LOCAL_STORAGE_KEY_DELIMITER) + 1));
		if (!Files.isDirectory(start)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(start)) {
			return paths.filter(Files::isRegularFile)
					.map(this::toSwiftObject)
					.filter(object -> object.name().startsWith(prefix))
					.toList();
		} catch (IOException | UncheckedIOException e) {
			throw new LocalStorageException("로컬 저장소 목록을 읽지 못했습니다: " + start, e);
		}
	}

	@Override
	public InputStream download(String objectName) {
		Path file = resolve(objectName);
		try {
			return Files.newInputStream(file);
		} catch (IOException e) {
			throw new LocalStorageException("로컬 저장소 파일을 열지 못했습니다: " + file, e);
		}
	}

	/** 키를 루트 아래 경로로 바꾼다. '..' 등으로 루트 밖을 가리키면 거부한다 */
	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new LocalStorageException("로컬 저장소 밖을 가리키는 키입니다: " + key);
		}
		return path;
	}

	private SwiftObject toSwiftObject(Path file) {
		String key = StreamSupport.stream(root.relativize(file).spliterator(), false)
				.map(Path::toString)
				.collect(Collectors.joining(LOCAL_STORAGE_KEY_DELIMITER));
		try {
			return new SwiftObject(key, Files.size(file));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
