-- scoreJob 결과 확인용 조회. scripts/run-score-demo.sh가 마지막에 실행한다.

-- 차량별 최신 리포트
SELECT r.vehicle_id                      AS vehicle,
       ROUND(r.score, 2)                 AS score,
       ROUND(r.penalty, 2)               AS penalty,
       ROUND(r.score_change, 2)          AS score_change,
       r.window_segment_count            AS segments,
       ROUND(r.window_distance_m / 1000, 1) AS window_km,
       ROUND(r.today_distance_m / 1000, 1)  AS today_km,
       ROUND(r.today_overspeed_km, 2)    AS today_overspeed_km,
       r.today_accel_count               AS today_accel,
       r.today_brake_count               AS today_brake,
       r.today_turn_count                AS today_turn,
       r.report_date
FROM vehicle_score_report r
WHERE r.id IN (SELECT MAX(id) FROM vehicle_score_report GROUP BY vehicle_id)
ORDER BY r.vehicle_id;

-- 차량별 구간 (닫힌 구간만)
SELECT vehicle_id                      AS vehicle,
       sequence_no                     AS seq,
       ROUND(distance_m / 1000, 1)     AS km,
       ROUND(overspeed_km, 2)          AS overspeed_km,
       accel_count                     AS accel,
       brake_count                     AS brake,
       turn_count                      AS turn,
       ROUND(penalty, 2)               AS penalty,
       ROUND(GREATEST(0, 100 - penalty), 2) AS segment_score
FROM score_segment
ORDER BY vehicle_id, sequence_no;

-- 발행 대기 중인 리포트 이벤트
SELECT id, aggregate_id AS report_id, event_type, payload, status
FROM outbox
WHERE aggregate_type = 'VEHICLE_SCORE_REPORT'
ORDER BY id;
