package com.pikachew.dangUBI.infra.local;

/**
 * 로컬 디렉터리 안의 파일 하나를 객체 저장소 객체처럼 나타낸 값.
 *
 * @param key  디렉터리 아래 상대 경로를 '/'로 이은 값 (S3·Swift 키와 같은 모양)
 * @param size 바이트 크기
 */
public record LocalObject(String key, long size) {
}
