package com.pikachew.dangUBI.batch.ingest.port;

import java.io.InputStream;
import java.util.List;

/**
 * Swift(원본 저장소)에서 수집 파일을 읽는 포트. 구현체는 infra/swift에 둔다.
 *
 * TODO: reconcileJob(PR #1)의 batch/common/port/RawObjectStore와 같은 저장소를 본다. 둘 다 머지되면 하나로 합친다.
 */
public interface RawObjectReader {

	/** prefix 아래 객체 키 전부. 순서는 보장하지 않는다. */
	List<String> listKeys(String prefix);

	/** 객체 내용을 연다. 호출한 쪽이 닫는다. */
	InputStream open(String key);

}
