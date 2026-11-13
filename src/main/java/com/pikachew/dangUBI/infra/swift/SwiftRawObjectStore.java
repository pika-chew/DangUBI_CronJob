package com.pikachew.dangUBI.infra.swift;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.pikachew.dangUBI.batch.reconcile.port.ObjectRef;
import com.pikachew.dangUBI.batch.reconcile.port.RawObjectStore;

/**
 * OpenStack Swift 원본 보관소 구현체 (껍데기).
 *
 * TODO: Swift s3api 지원 여부가 정해지면 SDK를 고른다.
 *       - 지원: AWS SDK v2 S3Client에 endpointOverride + forcePathStyle로 붙는다(S3SourceObjectStore와 코드 공유 가능).
 *       - 미지원: Keystone 토큰 + Swift REST API(GET /v1/{account}/{container}?prefix=...)를 직접 호출한다.
 * TODO: 목록이 많으면 marker/limit으로 나눠 받는다.
 */
@Component
public class SwiftRawObjectStore implements RawObjectStore {

	@Override
	public List<ObjectRef> listObjects(LocalDate targetDate) {
		throw new UnsupportedOperationException("TODO: Swift 목록 조회 미구현");
	}

}
