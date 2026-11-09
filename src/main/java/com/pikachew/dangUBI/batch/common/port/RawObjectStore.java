package com.pikachew.dangUBI.batch.common.port;

import java.time.LocalDate;
import java.util.List;

/**
 * 수집 워커가 S3에서 옮겨 온 원본 보관소(OpenStack Swift). 구현체는 infra.swift.
 *
 * Swift가 S3 호환 API(s3api)를 지원하는지 아직 정해지지 않았으므로 특정 SDK 타입을 노출하지 않는다.
 */
public interface RawObjectStore {

	/** 해당 날짜의 원본 객체 목록. */
	List<ObjectRef> listObjects(LocalDate targetDate);

}
