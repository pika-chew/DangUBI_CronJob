package com.pikachew.dangUBI.infra.local;

import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_KEY_DELIMITER;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * 로컬 디렉터리 하나를 버킷(컨테이너)처럼 다룬다. 디렉터리 아래 상대 경로를 '/'로 이은 값이 객체 키다.
 * local 프로필의 S3·Swift 대체 구현체가 같이 쓴다. 빈이 아니라 구현체가 디렉터리마다 만든다.
 */
public class LocalObjectDirectory {

	private final Path root;

	public LocalObjectDirectory(Path root) {
		this.root = root.toAbsolutePath().normalize();
	}

	/** 키가 prefix로 시작하는 파일 전부. 순서는 보장하지 않는다. prefix에 해당하는 디렉터리가 없으면 빈 목록이다 */
	public List<LocalObject> list(String prefix) {
		Path start = resolve(prefix.substring(0, prefix.lastIndexOf(LOCAL_STORAGE_KEY_DELIMITER) + 1));
		if (!Files.isDirectory(start)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(start)) {
			return paths.filter(Files::isRegularFile)
					.map(this::toLocalObject)
					.filter(object -> object.key().startsWith(prefix))
					.toList();
		} catch (IOException | UncheckedIOException e) {
			throw new LocalStorageException("로컬 저장소 목록을 읽지 못했습니다: " + start, e);
		}
	}

	/** 키에 해당하는 파일을 연다. 호출한 쪽이 닫는다 */
	public InputStream open(String key) {
		Path file = resolve(key);
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

	private LocalObject toLocalObject(Path file) {
		String key = StreamSupport.stream(root.relativize(file).spliterator(), false)
				.map(Path::toString)
				.collect(Collectors.joining(LOCAL_STORAGE_KEY_DELIMITER));
		try {
			return new LocalObject(key, Files.size(file));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
