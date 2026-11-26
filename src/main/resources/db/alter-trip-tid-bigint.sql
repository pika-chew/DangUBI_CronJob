-- 이 핫픽스를 배포하기 전에 운영 DB에서 한 번 실행한다.
-- Lambda가 보내는 tid가 int 범위(약 21억)를 넘는 값(예: 30836460003626)이라 INT 컬럼에 담기지 않는다.
-- trip-schema-mysql.sql은 CREATE TABLE IF NOT EXISTS라 이미 있는 테이블을 바꾸지 않으므로 이 파일로 바꾼다.
-- 로컬·테스트: 새로 만드는 테이블에는 이미 반영되어 있으므로 실행하지 않는다.
-- 유니크 키 uk_trip_vehicle_id_tid는 컬럼 타입이 바뀌어도 그대로 유지된다.
ALTER TABLE trip
    MODIFY COLUMN tid BIGINT NOT NULL;
