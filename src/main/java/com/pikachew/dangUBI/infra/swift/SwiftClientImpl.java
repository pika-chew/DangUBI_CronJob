package com.pikachew.dangUBI.infra.swift;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.SWIFT_S3_CLIENT_BEAN;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import java.io.InputStream;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.infra.storage.S3CompatibleBucket;
import com.pikachew.dangUBI.infra.swift.config.SwiftProperties;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * 운영 Swift에 s3api(S3 호환 API)로 붙는 {@link SwiftClient} 구현체. 접속 설정은 {@link com.pikachew.dangUBI.infra.swift.config.SwiftConfig}가 만든다.
 * 목록이 1000건을 넘으면 {@link S3CompatibleBucket}이 페이지를 넘기며 모두 읽는다.
 *
 * local 프로필에서는 로컬 디렉터리를 읽는 infra.local.LocalSwiftClient가 대신 쓰인다.
 */
@Component
@Profile(NOT_LOCAL_PROFILE)
public class SwiftClientImpl implements SwiftClient {

	private final S3CompatibleBucket container;

	public SwiftClientImpl(@Qualifier(SWIFT_S3_CLIENT_BEAN) S3Client swiftS3Client, SwiftProperties properties) {
		this.container = new S3CompatibleBucket(swiftS3Client, properties.container());
	}

	@Override
	public List<SwiftObject> listObjects(String prefix) {
		return container.list(prefix).stream()
				.map(object -> new SwiftObject(object.key(), object.size()))
				.toList();
	}

	@Override
	public InputStream download(String objectName) {
		return container.open(objectName);
	}

}
