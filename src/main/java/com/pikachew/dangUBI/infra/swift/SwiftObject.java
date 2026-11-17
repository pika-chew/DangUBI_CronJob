package com.pikachew.dangUBI.infra.swift;

/**
 * Swift 목록 조회 결과의 객체 하나. Swift 컨테이너 목록 API가 돌려주는 값 중 배치가 쓰는 것만 둔다.
 *
 * @param name  컨테이너 안의 객체 이름 (S3 키와 같다)
 * @param bytes 바이트 크기
 */
public record SwiftObject(String name, long bytes) {
}
