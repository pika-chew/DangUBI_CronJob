package com.pikachew.dangUBI.infra.local;

import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_SWIFT_DIR;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.LOCAL_PROFILE;

import java.io.InputStream;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;
import com.pikachew.dangUBI.infra.swift.SwiftClient;
import com.pikachew.dangUBI.infra.swift.SwiftObject;

/**
 * local 프로필에서 Swift 대신 로컬 디렉터리({root}/swift)를 읽는 {@link SwiftClient} 구현체.
 * 예: {root}/swift/firehose/processed/2026/11/08/03/a.jsonl.gz → 키 firehose/processed/2026/11/08/03/a.jsonl.gz
 */
@Component
@Profile(LOCAL_PROFILE)
public class LocalSwiftClient implements SwiftClient {

	private final LocalObjectDirectory directory;

	public LocalSwiftClient(LocalStorageProperties properties) {
		this.directory = new LocalObjectDirectory(properties.root().resolve(LOCAL_STORAGE_SWIFT_DIR));
	}

	@Override
	public List<SwiftObject> listObjects(String prefix) {
		return directory.list(prefix).stream()
				.map(object -> new SwiftObject(object.key(), object.size()))
				.toList();
	}

	@Override
	public InputStream download(String objectName) {
		return directory.open(objectName);
	}

}
