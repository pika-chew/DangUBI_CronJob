#!/usr/bin/env bash
# local 프로필용 샘플 수집 파일을 만든다. 형식은 docs/ingest-spec.md 2장(경로)·5장(레코드)을 따른다.
#
# 사용: scripts/make-local-sample.sh [로컬 저장소 루트]
#       루트를 주지 않으면 UBI_LOCAL_STORAGE_ROOT, 그것도 없으면 레포 옆 ../local-storage를 쓴다.
#
# 만드는 것 (targetDate = 2026-11-08, UTC):
#   s3/    03/sample-a.jsonl.gz   vehicle-001 tid 1 microtrip 3건
#          03/sample-b.jsonl.gz   vehicle-001 tid 1 trip 종료(ty=3) 1건 + 해석 못 하는 줄 1줄
#          14/sample-c.jsonl.gz   vehicle-002 tid 7 microtrip 2건 + trip 종료 1건
#          03/readme.txt          수집 파일이 아니라 목록에서 빠져야 한다
#   swift/ sample-a, sample-b만 복사한다. sample-c는 Swift로 옮겨지지 않은 상황이다.
#
# 기대 결과:
#   reconcileJob targetDate=2026-11-08 → S3 3건, Swift 2건, 누락 1건(sample-c)
#   ingestJob    targetDate=2026-11-08 → trip 1건(vehicle-001 tid 1, COMPLETED), microtrip 3건, 건너뛴 줄 1줄
#
# 같은 파일을 덮어쓰므로 여러 번 실행해도 된다.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT="${1:-${UBI_LOCAL_STORAGE_ROOT:-$REPO_ROOT/../local-storage}}"
DAY_PATH="firehose/processed/2026/11/08"

S3_DAY="$ROOT/s3/$DAY_PATH"
SWIFT_DAY="$ROOT/swift/$DAY_PATH"
mkdir -p "$S3_DAY/03" "$S3_DAY/14" "$SWIFT_DAY/03"
ROOT="$(cd "$ROOT" && pwd)"

# 시각(epoch ms, UTC): 03:00=1794106800000, 03:05=1794107100000, 03:10=1794107400000, 03:30=1794108600000
#                      14:00=1794146400000, 14:10=1794147000000, 14:40=1794148800000

gzip -n -c > "$S3_DAY/03/sample-a.jsonl.gz" <<'EOF'
{"vehicleId":"vehicle-001","ts":1794106800100,"ty":4,"schemaVersion":1,"idempotencyKey":"sample-mt-001-1","pld":{"tid":1,"clt":1794106800000,"sp":40,"em":"0","xyz":"10,-5,2"}}
{"vehicleId":"vehicle-001","ts":1794107100100,"ty":4,"schemaVersion":1,"idempotencyKey":"sample-mt-001-2","pld":{"tid":1,"clt":1794107100000,"sp":70,"em":"28","xyz":"35,12,-8"}}
{"vehicleId":"vehicle-001","ts":1794107400100,"ty":4,"schemaVersion":1,"idempotencyKey":"sample-mt-001-3","pld":{"tid":1,"clt":1794107400000,"sp":"55","em":"8","xyz":"-20,3,1"}}
EOF

gzip -n -c > "$S3_DAY/03/sample-b.jsonl.gz" <<'EOF'
{"vehicleId":"vehicle-001","ts":1794108600100,"ty":3,"schemaVersion":1,"idempotencyKey":"sample-trip-001","pld":{"tid":1,"stt":1794106800000,"edt":1794108600000,"dis":15000,"hsts":90,"mesp":30,"dtvt":1800}}
not a json line
EOF

gzip -n -c > "$S3_DAY/14/sample-c.jsonl.gz" <<'EOF'
{"vehicleId":"vehicle-002","ts":1794146400100,"ty":4,"schemaVersion":1,"idempotencyKey":"sample-mt-002-1","pld":{"tid":7,"clt":1794146400000,"sp":60,"em":"0","xyz":"0,0,0"}}
{"vehicleId":"vehicle-002","ts":1794147000100,"ty":4,"schemaVersion":1,"idempotencyKey":"sample-mt-002-2","pld":{"tid":7,"clt":1794147000000,"sp":80,"em":"20","xyz":"5,40,3"}}
{"vehicleId":"vehicle-002","ts":1794148800100,"ty":3,"schemaVersion":1,"idempotencyKey":"sample-trip-002","pld":{"tid":7,"stt":1794146400000,"edt":1794148800000,"dis":32000,"hsts":110,"mesp":48,"dtvt":2400}}
EOF

echo "not a telemetry file" > "$S3_DAY/03/readme.txt"

# 수집 워커가 sample-a, sample-b만 옮긴 상황
cp "$S3_DAY/03/sample-a.jsonl.gz" "$S3_DAY/03/sample-b.jsonl.gz" "$SWIFT_DAY/03/"

echo "샘플을 만들었습니다: $ROOT"
(cd "$ROOT" && find s3 swift -type f | sort)
