package com.pikachew.dangUBI.infra.swift;

import java.io.InputStream;
import java.util.List;

import com.pikachew.dangUBI.batch.ingest.port.RawObjectReader;
import org.springframework.stereotype.Component;

/**
 * Swift 구현체. 아직 껍데기다.
 *
 * TODO: Swift 클라이언트(SDK 또는 REST)를 정하고 구현한다.
 * TODO: 엔드포인트·컨테이너·인증 정보는 SwiftProperties로 주입한다. 코드에 넣지 않는다.
 * TODO: listKeys는 목록이 1만 건을 넘으면 marker로 페이지를 넘기며 모두 읽는다.
 */
@Component
public class SwiftRawObjectReader implements RawObjectReader {

	@Override
	public List<String> listKeys(String prefix) {
		throw new UnsupportedOperationException("Swift 목록 조회가 아직 구현되지 않았습니다");
	}

	@Override
	public InputStream open(String key) {
		throw new UnsupportedOperationException("Swift 객체 읽기가 아직 구현되지 않았습니다");
	}

}
