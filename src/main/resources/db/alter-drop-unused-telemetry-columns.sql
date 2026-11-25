-- trip, trip_microtrip에서 쓰지 않는 컬럼을 지운다 (docs/ingest-spec.md 6장).
-- - trip: Lambda가 보내지 않아 항상 NULL인 요약값
-- - trip_microtrip: 점수 계산에 쓰지 않는 측정값
-- 운영: trip-schema-mysql.sql은 CREATE TABLE IF NOT EXISTS라 이미 있는 테이블을 바꾸지 않으므로, 배포 전에 이 파일을 한 번 직접 실행한다.
-- 로컬·테스트: 새로 만드는 테이블에는 이 컬럼들이 없으므로 실행하지 않는다.
-- 지운 값은 Swift 원본에 남아 있다. 다시 필요하면 컬럼을 추가하고 Swift에서 다시 적재한다.

ALTER TABLE trip
    DROP COLUMN total_distance,
    DROP COLUMN fuel_consumption,
    DROP COLUMN start_lat,
    DROP COLUMN start_lon,
    DROP COLUMN end_lat,
    DROP COLUMN end_lon,
    DROP COLUMN coolant_temp_avg,
    DROP COLUMN carbon_emission,
    DROP COLUMN fuel_cut_time,
    DROP COLUMN idle_time,
    DROP COLUMN battery_voltage,
    DROP COLUMN generator_voltage,
    DROP COLUMN warmup_time,
    DROP COLUMN high_speed_time,
    DROP COLUMN firmware_version;

ALTER TABLE trip_microtrip
    DROP COLUMN lat,
    DROP COLUMN lon,
    DROP COLUMN location_accuracy,
    DROP COLUMN fuel_consumption,
    DROP COLUMN trip_distance,
    DROP COLUMN rpm,
    DROP COLUMN engine_load,
    DROP COLUMN acc_x,
    DROP COLUMN acc_y,
    DROP COLUMN gyro_y,
    DROP COLUMN battery_voltage,
    DROP COLUMN accel_pedal_position;
