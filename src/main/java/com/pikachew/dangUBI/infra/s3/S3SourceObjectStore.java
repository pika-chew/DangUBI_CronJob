package com.pikachew.dangUBI.infra.s3;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.SourceObjectStore;

/**
 * AWS S3 원본 버킷 구현체 (껍데기).
 *
 * TODO: AWS SDK v2(software.amazon.awssdk:s3)를 추가하고 ListObjectsV2 페이지네이터로 targetDate 접두어의 목록을 받는다.
 * TODO: 버킷·리전·키 접두어 규칙은 설정(ubi.s3.*)으로, 자격 증명은 Vault → Secret → 환경 변수로 받는다.
 */
@Component
public class S3SourceObjectStore implements SourceObjectStore {

	@Override
	public List<ObjectRef> listObjects(LocalDate targetDate) {
		throw new UnsupportedOperationException("TODO: S3 목록 조회 미구현");
	}

}
