package com.pikachew.dangUBI.domain.trip.model;

/**
 * 한 번의 적재 결과.
 *
 * @param completedTrips      반영한 ty=3 건수 (같은 trip을 다시 받아도 센다)
 * @param insertedMicrotrips  새로 저장한 microtrip 건수
 * @param duplicateMicrotrips 이미 있거나 같은 입력 안에서 겹쳐 건너뛴 microtrip 건수
 */
public record TripIngestResult(int completedTrips, int insertedMicrotrips, int duplicateMicrotrips) {
}
