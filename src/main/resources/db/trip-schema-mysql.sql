-- ingestJob이 적재하는 테이블 (docs/ingest-spec.md 7장). 시각은 모두 UTC.
-- 운영: 배포 전에 직접 실행한다. 로컬·테스트: spring.sql.init으로 자동 실행한다.
-- 여러 번 실행해도 되도록 IF NOT EXISTS로 만든다.

CREATE TABLE IF NOT EXISTS driver (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_id  VARCHAR(64)  NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_driver_vehicle_id UNIQUE (vehicle_id)
);

CREATE TABLE IF NOT EXISTS trip (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    driver_id             BIGINT        NOT NULL,
    vehicle_id            VARCHAR(64)   NOT NULL,
    tid                   INT           NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    started_at            DATETIME(3)   NULL,
    ended_at              DATETIME(3)   NULL,
    first_collected_at    DATETIME(3)   NULL,
    last_collected_at     DATETIME(3)   NULL,
    distance              INT           NULL,
    total_distance        INT           NULL,
    fuel_consumption      INT           NULL,
    start_lat             INT           NULL,
    start_lon             INT           NULL,
    end_lat               INT           NULL,
    end_lon               INT           NULL,
    coolant_temp_avg      INT           NULL,
    carbon_emission       INT           NULL,
    fuel_cut_time         INT           NULL,
    max_speed             INT           NULL,
    avg_speed             INT           NULL,
    idle_time             INT           NULL,
    battery_voltage       INT           NULL,
    generator_voltage     INT           NULL,
    warmup_time           INT           NULL,
    high_speed_time       INT           NULL,
    firmware_version      INT           NULL,
    driving_time          INT           NULL,
    trip_idempotency_key  VARCHAR(128)  NULL,
    trip_received_at      DATETIME(3)   NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_at            DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_vehicle_id_tid UNIQUE (vehicle_id, tid),
    CONSTRAINT fk_trip_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    INDEX idx_trip_driver_id_started_at (driver_id, started_at)
);

CREATE TABLE IF NOT EXISTS trip_microtrip (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id                BIGINT        NOT NULL,
    idempotency_key        VARCHAR(128)  NOT NULL,
    collected_at           DATETIME(3)   NOT NULL,
    received_at            DATETIME(3)   NULL,
    lat                    INT           NULL,
    lon                    INT           NULL,
    location_accuracy      INT           NULL,
    fuel_consumption       INT           NULL,
    trip_distance          INT           NULL,
    rpm                    INT           NULL,
    speed                  INT           NULL,
    event_mask             SMALLINT      NULL,
    engine_load            INT           NULL,
    acc_x                  INT           NULL,
    acc_y                  INT           NULL,
    gyro_y                 INT           NULL,
    battery_voltage        INT           NULL,
    accel_pedal_position   INT           NULL,
    created_at             DATETIME(3)   NOT NULL,
    updated_at             DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_microtrip_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_trip_microtrip_trip FOREIGN KEY (trip_id) REFERENCES trip (id),
    INDEX idx_trip_microtrip_trip_id_collected_at (trip_id, collected_at)
);
