package com.pikachew.dangUBI.infra.storage;

/**
 * S3 호환 저장소 목록 조회 결과의 객체 하나. SDK 타입을 바깥으로 내보내지 않도록 쓰는 값만 옮긴다.
 *
 * @param key  버킷(컨테이너) 안의 객체 키
 * @param size 바이트 크기
 */
public record StorageObject(String key, long size) {
}
