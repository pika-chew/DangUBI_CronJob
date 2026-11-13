package com.pikachew.dangUBI.infra.swift;

import static com.pikachew.dangUBI.common.consts.ProfileStaticValues.NOT_LOCAL_PROFILE;

import java.io.InputStream;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 운영 Swift에 붙는 {@link SwiftClient} 구현체. 아직 껍데기다.
 *
 * TODO: Swift 클라이언트(SDK 또는 REST)를 정하고 구현한다.
 * TODO: 엔드포인트·컨테이너·인증 정보는 SwiftProperties로 주입한다. 코드에 넣지 않는다.
 * TODO: 목록이 1만 건을 넘으면 marker로 페이지를 넘기며 모두 읽는다.
 *
 * local 프로필에서는 로컬 디렉터리를 읽는 infra.local.LocalSwiftClient가 대신 쓰인다.
 */
@Component
@Profile(NOT_LOCAL_PROFILE)
public class SwiftClientImpl implements SwiftClient {

	@Override
	public List<SwiftObject> listObjects(String prefix) {
		throw new UnsupportedOperationException("Swift 목록 조회가 아직 구현되지 않았습니다");
	}

	@Override
	public InputStream download(String objectName) {
		throw new UnsupportedOperationException("Swift 객체 읽기가 아직 구현되지 않았습니다");
	}

}
