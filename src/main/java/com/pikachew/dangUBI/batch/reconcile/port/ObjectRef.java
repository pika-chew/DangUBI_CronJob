package com.pikachew.dangUBI.batch.reconcile.port;

/**
 * 오브젝트 스토리지 객체 하나의 식별 정보. S3·Swift 어느 쪽 SDK 타입에도 묶이지 않도록 따로 둔다.
 *
 * @param key  버킷(컨테이너) 안의 객체 키
 * @param size 바이트 크기
 */
public record ObjectRef(String key, long size) {
}
