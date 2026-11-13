package com.pikachew.dangUBI.infra.swift;

import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Swift 객체 저장소 접근. 아직 껍데기다.
 *
 * TODO: Swift 클라이언트(SDK 또는 REST)를 정하고 구현한다.
 * TODO: 엔드포인트·컨테이너·인증 정보는 SwiftProperties로 주입한다. 코드에 넣지 않는다.
 * TODO: 목록이 1만 건을 넘으면 marker로 페이지를 넘기며 모두 읽는다.
 */
@Component
public class SwiftClient {

	/** prefix 아래 객체 이름 전부. 순서는 보장하지 않는다 */
	public List<String> listObjectNames(String prefix) {
		throw new UnsupportedOperationException("Swift 목록 조회가 아직 구현되지 않았습니다");
	}

	/** 객체 내용을 연다. 호출한 쪽이 닫는다 */
	public InputStream download(String objectName) {
		throw new UnsupportedOperationException("Swift 객체 읽기가 아직 구현되지 않았습니다");
	}

}
