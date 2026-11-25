-- ingestJob이 적재하는 테이블 (docs/ingest-spec.md 7장)과 scoreJob이 만드는 테이블 (docs/score-spec.md). 시각은 모두 UTC.
-- 운영: 배포 전에 직접 실행한다. 로컬·테스트: spring.sql.init으로 자동 실행한다.
-- 여러 번 실행해도 되도록 IF NOT EXISTS로 만든다.
-- 식별자 컬럼(vehicle_id, *idempotency_key)은 utf8mb4_bin으로 대소문자를 구분한다.
-- 기본 정렬 규칙(utf8mb4_0900_ai_ci)은 대소문자를 무시해, Writer(Java)의 중복 판단과 DB 유니크 키가 어긋난다.

CREATE TABLE IF NOT EXISTS driver (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_id  VARCHAR(64)  COLLATE utf8mb4_bin  NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_driver_vehicle_id UNIQUE (vehicle_id)
);

-- ty=3 요약값은 Lambda가 보내는 필드(stt, edt, dis, hsts, mesp, dtvt)만 저장한다 (docs/ingest-spec.md 6장).
CREATE TABLE IF NOT EXISTS trip (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    driver_id             BIGINT        NOT NULL,
    vehicle_id            VARCHAR(64)   COLLATE utf8mb4_bin  NOT NULL,
    tid                   INT           NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    started_at            DATETIME(3)   NULL,
    ended_at              DATETIME(3)   NULL,
    first_collected_at    DATETIME(3)   NULL,
    last_collected_at     DATETIME(3)   NULL,
    distance              INT           NULL,
    max_speed             INT           NULL,
    avg_speed             INT           NULL,
    driving_time          INT           NULL,
    trip_idempotency_key  VARCHAR(128)  COLLATE utf8mb4_bin  NULL,
    trip_received_at      DATETIME(3)   NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_at            DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_vehicle_id_tid UNIQUE (vehicle_id, tid),
    CONSTRAINT fk_trip_driver FOREIGN KEY (driver_id) REFERENCES driver (id),
    INDEX idx_trip_driver_id_started_at (driver_id, started_at)
);

-- 점수 계산에 쓰는 값(clt, sp, em)만 저장한다. 나머지 필드는 Swift 원본에만 남는다 (docs/ingest-spec.md 6장).
CREATE TABLE IF NOT EXISTS trip_microtrip (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    trip_id                BIGINT        NOT NULL,
    idempotency_key        VARCHAR(128)  COLLATE utf8mb4_bin  NOT NULL,
    collected_at           DATETIME(3)   NOT NULL,
    received_at            DATETIME(3)   NULL,
    speed                  INT           NULL,
    event_mask             SMALLINT      NULL,
    created_at            DATETIME(3)   NOT NULL,
    updated_at             DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_microtrip_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_trip_microtrip_trip FOREIGN KEY (trip_id) REFERENCES trip (id),
    INDEX idx_trip_microtrip_trip_id_collected_at (trip_id, collected_at)
);

-- 적재와 같은 트랜잭션에서 남기는 발행 대기 이벤트. 발행은 별도 relay가 PENDING 행을 id 순서로 읽어 한다.
-- 같은 대상의 같은 이벤트는 한 번만 남긴다 (uk_outbox_aggregate_event).
CREATE TABLE IF NOT EXISTS outbox (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(32)    NOT NULL,
    aggregate_id    BIGINT         NOT NULL,
    event_type      VARCHAR(32)    NOT NULL,
    payload         VARCHAR(1024)  NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    published_at    DATETIME(3)    NULL,
    created_at      DATETIME(3)    NOT NULL,
    updated_at      DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_aggregate_event UNIQUE (aggregate_type, aggregate_id, event_type),
    INDEX idx_outbox_status_id (status, id)
);

-- scoreJob: 종료된 trip 하나의 점수 지표. trip마다 한 번만 만든다 (docs/score-spec.md 4장).
-- weighted_night_*는 야간 발생분에 시간대별 야간 추가 감점률을 곱해 더한 값이다.
CREATE TABLE IF NOT EXISTS trip_score_metric (
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id                      BIGINT       NOT NULL,
    driver_id                    BIGINT       NOT NULL,
    ended_at                     DATETIME(3)  NOT NULL,
    distance_m                   BIGINT       NOT NULL,
    overspeed_km                 DOUBLE       NOT NULL,
    accel_count                  INT          NOT NULL,
    brake_count                  INT          NOT NULL,
    turn_count                   INT          NOT NULL,
    weighted_night_overspeed_km  DOUBLE       NOT NULL,
    weighted_night_accel         DOUBLE       NOT NULL,
    weighted_night_brake         DOUBLE       NOT NULL,
    weighted_night_turn          DOUBLE       NOT NULL,
    unevaluated_count            INT          NOT NULL,
    created_at                   DATETIME(3)  NOT NULL,
    updated_at                   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_score_metric_trip_id UNIQUE (trip_id),
    CONSTRAINT fk_trip_score_metric_trip FOREIGN KEY (trip_id) REFERENCES trip (id),
    CONSTRAINT fk_trip_score_metric_driver FOREIGN KEY (driver_id) REFERENCES driver (id)
);
