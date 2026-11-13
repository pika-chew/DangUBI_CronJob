package com.pikachew.dangUBI.infra.local;

import static com.pikachew.dangUBI.common.consts.LocalStorageStaticValues.LOCAL_STORAGE_S3_DIR;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.LOCAL_PROFILE;

import java.time.LocalDate;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.infra.local.config.LocalStorageProperties;
import com.pikachew.dangUBI.infra.storage.TelemetryObjectKeys;

/**
 * local 프로필에서 S3 원본 버킷 대신 로컬 디렉터리({root}/s3)를 읽는 {@link SourceObjectStore} 구현체.
 * S3와 Swift는 같은 키를 쓰므로 대상 파일 판별은 Swift와 같은 {@link TelemetryObjectKeys} 규칙을 따른다 (docs/ingest-spec.md 2장).
 */
@Component
@Profile(LOCAL_PROFILE)
public class LocalSourceObjectStore implements SourceObjectStore {

	private final LocalObjectDirectory directory;

	public LocalSourceObjectStore(LocalStorageProperties properties) {
		this.directory = new LocalObjectDirectory(properties.root().resolve(LOCAL_STORAGE_S3_DIR));
	}

	@Override
	public List<ObjectRef> listObjects(LocalDate targetDate) {
		return directory.list(TelemetryObjectKeys.dayPrefix(targetDate)).stream()
				.filter(object -> TelemetryObjectKeys.isTelemetryFile(object.key(), targetDate))
				.map(object -> new ObjectRef(object.key(), object.size()))
				.toList();
	}

}
