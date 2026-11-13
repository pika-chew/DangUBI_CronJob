package com.pikachew.dangUBI.batch.reconcile.port;

import java.time.LocalDate;
import java.util.List;

/** 차량 원본 데이터가 처음 들어오는 저장소(AWS S3). reconcile의 비교 기준이다. 구현체는 infra.s3. */
public interface SourceObjectStore {

	/** 해당 날짜에 들어온 원본 객체 목록. 날짜 → 키 접두어 규칙은 구현체가 정한다. */
	List<ObjectRef> listObjects(LocalDate targetDate);

}
