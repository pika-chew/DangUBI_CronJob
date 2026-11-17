package com.pikachew.dangUBI.infra.s3;

import static com.pikachew.dangUBI.common.consts.ObjectStorageStaticValues.S3_SOURCE_CLIENT_BEAN;
import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;
import com.pikachew.dangUBI.infra.s3.config.S3Properties;
import com.pikachew.dangUBI.infra.storage.S3CompatibleBucket;
import com.pikachew.dangUBI.infra.storage.TelemetryObjectKeys;

import software.amazon.awssdk.services.s3.S3Client;

/** AWS S3 원본 버킷의 {@link SourceObjectStore} 구현체. S3와 Swift는 같은 키 규칙을 쓴다 (docs/ingest-spec.md 2장) */
@Component
@Profile(NOT_LOCAL_PROFILE)
public class S3SourceObjectStore implements SourceObjectStore {

	private final S3CompatibleBucket bucket;

	public S3SourceObjectStore(@Qualifier(S3_SOURCE_CLIENT_BEAN) S3Client sourceS3Client, S3Properties properties) {
		this.bucket = new S3CompatibleBucket(sourceS3Client, properties.bucket());
	}

	@Override
	public List<ObjectRef> listObjects(LocalDate targetDate) {
		return bucket.list(TelemetryObjectKeys.dayPrefix(targetDate)).stream()
				.filter(object -> TelemetryObjectKeys.isTelemetryFile(object.key(), targetDate))
				.map(object -> new ObjectRef(object.key(), object.size()))
				.toList();
	}

}
