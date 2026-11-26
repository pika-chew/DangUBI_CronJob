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
    tid                   BIGINT        NOT NULL,
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
    last_ingested_at      DATETIME(3)   NULL,
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

-- scoreJob: 차량(VIN)별로 trip 지표를 쌓아 구간 거리를 넘었을 때 닫은 구간 (docs/score-spec.md 5장).
-- 집계 컬럼은 trip_score_metric과 같다. 닫힌 구간만 저장한다.
CREATE TABLE IF NOT EXISTS score_segment (
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    vehicle_id                   VARCHAR(64)  COLLATE utf8mb4_bin  NOT NULL,
    sequence_no                  INT          NOT NULL,
    distance_m                   BIGINT       NOT NULL,
    overspeed_km                 DOUBLE       NOT NULL,
    accel_count                  INT          NOT NULL,
    brake_count                  INT          NOT NULL,
    turn_count                   INT          NOT NULL,
    weighted_night_overspeed_km  DOUBLE       NOT NULL,
    weighted_night_accel         DOUBLE       NOT NULL,
    weighted_night_brake         DOUBLE       NOT NULL,
    weighted_night_turn          DOUBLE       NOT NULL,
    penalty                      DOUBLE       NOT NULL,
    created_at                   DATETIME(3)  NOT NULL,
    updated_at                   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_score_segment_vehicle_id_sequence_no UNIQUE (vehicle_id, sequence_no)
);

-- scoreJob: 차량 하루 리포트 (docs/score-spec.md 7장). 새로 지표를 만든 차량마다 실행당 하나 남긴다.
-- 점수는 차량(VIN) 단위다. 한 사람이 여러 차량을 타는 경우의 합산은 받는 쪽(query server)이 한다.
-- score·penalty는 닫힌 구간이 없으면 NULL(미산출)이다. today_*는 이 리포트에 담긴 trip, window_*는 반영 범위 안 구간의 합이다.
CREATE TABLE IF NOT EXISTS vehicle_score_report (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    vehicle_id            VARCHAR(64)    COLLATE utf8mb4_bin  NOT NULL,
    report_date           DATE           NOT NULL,
    score                 DOUBLE         NULL,
    penalty               DOUBLE         NULL,
    score_change          DOUBLE         NULL,
    recent_scores         VARCHAR(1024)  NOT NULL,
    window_segment_count  INT            NOT NULL,
    today_distance_m      BIGINT         NOT NULL,
    today_overspeed_km    DOUBLE         NOT NULL,
    today_accel_count     INT            NOT NULL,
    today_brake_count     INT            NOT NULL,
    today_turn_count      INT            NOT NULL,
    window_distance_m     BIGINT         NOT NULL,
    window_overspeed_km   DOUBLE         NOT NULL,
    window_accel_count    INT            NOT NULL,
    window_brake_count    INT            NOT NULL,
    window_turn_count     INT            NOT NULL,
    created_at            DATETIME(3)    NOT NULL,
    updated_at            DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_vehicle_score_report_vehicle_id_report_date (vehicle_id, report_date)
);

-- scoreJob: 종료된 trip 하나의 점수 지표. trip마다 한 번만 만든다 (docs/score-spec.md 4장).
-- weighted_night_*는 야간 발생분에 시간대별 야간 추가 감점률을 곱해 더한 값이다.
-- segment_id는 구간 거리를 채워 구간이 닫히면, report_id는 하루 리포트에 담기면 채운다.
CREATE TABLE IF NOT EXISTS trip_score_metric (
    id                           BIGINT       NOT NULL AUTO_INCREMENT,
    trip_id                      BIGINT       NOT NULL,
    vehicle_id                   VARCHAR(64)  COLLATE utf8mb4_bin  NOT NULL,
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
    segment_id                   BIGINT       NULL,
    report_id                    BIGINT       NULL,
    created_at                   DATETIME(3)  NOT NULL,
    updated_at                   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_trip_score_metric_trip_id UNIQUE (trip_id),
    CONSTRAINT fk_trip_score_metric_trip FOREIGN KEY (trip_id) REFERENCES trip (id),
    CONSTRAINT fk_trip_score_metric_segment FOREIGN KEY (segment_id) REFERENCES score_segment (id),
    CONSTRAINT fk_trip_score_metric_report FOREIGN KEY (report_id) REFERENCES vehicle_score_report (id),
    INDEX idx_trip_score_metric_vehicle_id_segment_id (vehicle_id, segment_id),
    INDEX idx_trip_score_metric_report_id_vehicle_id (report_id, vehicle_id)
);
