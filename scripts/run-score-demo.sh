#!/usr/bin/env bash
# 더미 데이터로 ingestJob → scoreJob을 local 프로필에서 돌리고 점수를 보여준다 (docs/local-run.md "점수 시연").
#
# 사용: scripts/run-score-demo.sh
#   1. scripts/make-score-sample.py로 차량 3대(안전·보통·난폭)의 3일치 수집 파일을 만들고 예상 점수를 출력한다
#   2. 날짜마다 ingestJob을 실행한다
#   3. scoreJob을 test 기준(구간 50km, 반영 범위 600km), 대기 시간 0초로 실행한다
#   4. scripts/score-report.sql로 리포트·구간·outbox를 조회한다. 1번의 예상 점수와 비교한다
#
# 미리 MySQL을 띄워둔다: docker compose up -d
# 이전 데이터가 섞이지 않게 처음부터 다시 하려면: docker compose down -v && docker compose up -d
#
# 환경 변수
#   UBI_LOCAL_STORAGE_ROOT  로컬 저장소 루트 (기본: 레포 옆 ../local-storage)
#   SPRING_DATASOURCE_URL   MySQL 주소를 바꿀 때 (예: jdbc:mysql://localhost:3307/ubi)
#   MYSQL_CMD               결과 조회에 쓸 mysql 명령 (기본: compose의 mysql 컨테이너)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
export UBI_LOCAL_STORAGE_ROOT="${UBI_LOCAL_STORAGE_ROOT:-$REPO_ROOT/../local-storage}"
MYSQL_CMD="${MYSQL_CMD:-docker compose exec -T mysql mysql -t -uubi -pubi ubi}"

echo "== 1. 더미 수집 파일 생성"
python3 scripts/make-score-sample.py --root "$UBI_LOCAL_STORAGE_ROOT"

echo
echo "== 2. 애플리케이션 빌드"
./gradlew -q bootJar
JAR="$(ls build/libs/*.jar | grep -v -- '-plain' | head -1)"

# 같은 날짜를 다시 적재할 수 있도록 실행마다 다른 rerun 값을 붙인다. 이미 넣은 레코드는 idempotencyKey로 걸러진다
RERUN="$(date +%s)"
while read -r target_date; do
	[ -z "$target_date" ] && continue
	echo
	echo "== 3. ingestJob targetDate=$target_date"
	java -jar "$JAR" --spring.profiles.active=local --spring.batch.job.name=ingestJob \
		"targetDate=$target_date" "rerun=$RERUN" | grep -E "ingest chunk|Job: \[" || true
done < "$UBI_LOCAL_STORAGE_ROOT/score-sample-dates.txt"

echo
echo "== 4. scoreJob (test 기준, 대기 0초)"
java -jar "$JAR" --spring.profiles.active=local --spring.batch.job.name=scoreJob \
	--ubi.batch.score.basis=test --ubi.batch.score.settle-delay=0s | grep -E "score (metric|report) chunk|Job: \[" || true

echo
echo "== 5. 결과 (1번의 예상 점수와 비교)"
$MYSQL_CMD < scripts/score-report.sql 2> >(grep -v "Using a password" >&2)
