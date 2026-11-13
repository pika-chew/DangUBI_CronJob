package com.pikachew.dangUBI.infra.swift;

import java.io.InputStream;
import java.util.List;

/**
 * Swift 객체 저장소 접근. 구현체는 운영용 {@link SwiftClientImpl}, local 프로필용 infra.local.LocalSwiftClient다.
 * 목록 조회·키 규칙·파일 해석은 이 인터페이스 위({@link SwiftTelemetryFileReader}, {@link SwiftRawObjectStore})에서 하므로
 * 구현체는 객체 목록과 내용만 돌려준다.
 */
public interface SwiftClient {

	/** prefix 아래 객체 전부(이름, 크기). 순서는 보장하지 않는다 */
	List<SwiftObject> listObjects(String prefix);

	/** 객체 내용을 연다. 호출한 쪽이 닫는다 */
	InputStream download(String objectName);

}
